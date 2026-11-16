package com.fooda.api.sales.application.service;

import com.fooda.api.core.application.port.out.BranchRepository;
import com.fooda.api.core.application.port.out.CustomerRepository;
import com.fooda.api.core.application.port.out.ExpenseRepository;
import com.fooda.api.core.application.port.out.MonthCloseRepository;
import com.fooda.api.core.application.port.out.PackRepository;
import com.fooda.api.core.application.port.out.PaymentRepository;
import com.fooda.api.core.application.port.out.ProductionRepository;
import com.fooda.api.core.application.port.out.SaleRepository;
import com.fooda.api.core.application.port.out.SettingsRepository;
import com.fooda.api.core.application.port.out.StockLock;
import com.fooda.api.core.application.port.out.StockMovementRepository;
import com.fooda.api.core.application.port.out.SupplyRepository;
import com.fooda.api.core.domain.catalog.Branch;
import com.fooda.api.core.domain.catalog.Pack;
import com.fooda.api.core.domain.catalog.Settings;
import com.fooda.api.core.domain.expense.Expense;
import com.fooda.api.core.domain.expense.PaymentMethod;
import com.fooda.api.core.domain.finance.MonthClose;
import com.fooda.api.core.domain.finance.SaleProfits;
import com.fooda.api.core.domain.production.Production;
import com.fooda.api.core.domain.sales.Customer;
import com.fooda.api.core.domain.sales.Payment;
import com.fooda.api.core.domain.sales.Sale;
import com.fooda.api.core.domain.sales.SaleLine;
import com.fooda.api.core.domain.sales.SaleRules;
import com.fooda.api.core.domain.sales.SaleType;
import com.fooda.api.core.domain.stock.MovementType;
import com.fooda.api.core.domain.stock.OutLine;
import com.fooda.api.core.domain.stock.Shortage;
import com.fooda.api.core.domain.stock.StockMovement;
import com.fooda.api.core.domain.stock.StockRules;
import com.fooda.api.sales.application.port.in.SaleFilter;
import com.fooda.api.sales.application.port.in.SaleRequest;
import com.fooda.api.sales.application.port.in.SaleUseCase;
import com.fooda.api.sales.application.port.in.SaleView;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.Listing;
import com.fooda.api.shared.application.Lookups;
import com.fooda.api.shared.application.Page;
import com.fooda.api.shared.application.exception.ResourceNotFoundException;
import com.fooda.api.shared.application.port.out.Transactions;
import com.fooda.api.shared.domain.Decimals;
import com.fooda.api.shared.domain.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SaleService implements SaleUseCase {

    private static final String UNKNOWN = "—";
    private static final Comparator<Sale> BY_ID = Comparator.comparingLong(Sale::id);
    private static final Map<String, Comparator<Sale>> SORTABLE = Map.of(
            "date", Comparator.comparing(Sale::date),
            "id", BY_ID);

    private final SaleRepository sales;
    private final PaymentRepository payments;
    private final ExpenseRepository expenses;
    private final CustomerRepository customers;
    private final BranchRepository branches;
    private final PackRepository packs;
    private final ProductionRepository productions;
    private final SupplyRepository supplies;
    private final SettingsRepository settings;
    private final StockMovementRepository movements;
    private final MonthCloseRepository monthCloses;
    private final StockLock stockLock;
    private final BusinessCalendar calendar;
    private final Transactions tx;

    public SaleService(SaleRepository sales, PaymentRepository payments, ExpenseRepository expenses,
                       CustomerRepository customers, BranchRepository branches, PackRepository packs,
                       ProductionRepository productions, SupplyRepository supplies, SettingsRepository settings,
                       StockMovementRepository movements, MonthCloseRepository monthCloses, StockLock stockLock,
                       BusinessCalendar calendar, Transactions tx) {
        this.sales = sales;
        this.payments = payments;
        this.expenses = expenses;
        this.customers = customers;
        this.branches = branches;
        this.packs = packs;
        this.productions = productions;
        this.supplies = supplies;
        this.settings = settings;
        this.movements = movements;
        this.monthCloses = monthCloses;
        this.stockLock = stockLock;
        this.calendar = calendar;
        this.tx = tx;
    }

    @Override
    public Page<SaleView> list(SaleFilter f, ListQuery q) {
        List<Sale> filtered = sales.findAll().stream().filter(s -> matches(s, f)).toList();
        Page<Sale> page = Listing.page(filtered, q, SORTABLE, "date", "desc", BY_ID);
        Views v = new Views();
        return page.map(s -> v.of(s, List.of()));
    }

    @Override
    public SaleView get(long id) {
        Sale s = find(id);
        return new Views().of(s, payments.findBySale(id));
    }

    @Override
    public SaleView create(SaleRequest r) {
        return tx.get(() -> {
            stockLock.lock();
            Branch branch = Lookups.found(r.branchId(), branches::findById, "Sede no encontrada");
            if (r.customerId() != null) {
                Lookups.found(r.customerId(), customers::findById, "Cliente no encontrado");
            }
            SaleType type = SaleType.of(r.type());
            PaymentMethod method = PaymentMethod.of(r.method(), "method");
            boolean paysWithPacks = type == SaleType.PAID_WITH_PACKS;
            if (r.lines() == null || r.lines().isEmpty()) {
                throw new ValidationException("lines", "Agregá al menos un pack");
            }
            String notes = blankToNull(r.notes());
            if (paysWithPacks && notes == null) {
                throw new ValidationException("notes", "Indicá qué recibiste a cambio");
            }
            if (paysWithPacks) {
                if (notes.length() > 200) {
                    throw new ValidationException("notes", "Las notas del pago con packs tienen hasta 200 caracteres");
                }
                String et = blankToNull(r.expenseType());
                if (et != null && et.length() > 60) {
                    throw new ValidationException("expenseType", "El tipo de gasto tiene hasta 60 caracteres");
                }
            }
            boolean income = SaleRules.isIncome(type);
            List<SaleLine> lines = new ArrayList<>();
            BigDecimal packsValue = BigDecimal.ZERO;
            for (SaleRequest.LineRequest l : r.lines()) {
                Pack pack = Lookups.found(l.packId(), packs::findById, "Pack no encontrado");
                if (l.productionId() == null) {
                    throw new ResourceNotFoundException("Producción no encontrada");
                }
                if (l.quantity() == null) {
                    throw new ValidationException("lines", "Las cantidades deben ser enteros mayores a cero");
                }
                BigDecimal price = BigDecimal.ZERO;
                if (income) {
                    price = l.unitPrice() != null ? l.unitPrice() : packPrice(pack);
                }
                lines.add(new SaleLine(l.productionId(), pack.id(), l.quantity(), price));
                if (paysWithPacks) {
                    packsValue = packsValue.add(packPrice(pack).multiply(BigDecimal.valueOf(l.quantity())));
                }
            }
            List<OutLine> out = lines.stream()
                    .map(l -> new OutLine(l.productionId(), l.packId(), l.quantity())).toList();
            LocalDate day = calendar.pastOrToday(r.date());
            StockRules.requireAfterLotsClosed(out.stream().map(OutLine::productionId).distinct()
                    .map(productions::findById).flatMap(Optional::stream).toList(), day);
            Shortage s = StockRules.firstShortage(movements.findAll(), branch.id(), out, day).orElse(null);
            if (s != null) {
                throw new ValidationException("lines", StockRules.SHORTAGE_MESSAGE
                        .formatted(s.available(), packName(s.packId()), lotCode(s.productionId()), branch.name()));
            }
            monthCloses.requireOpen(day);
            Sale draft = new Sale(null, day, branch.id(), r.customerId(), type, blankToNull(r.reason()), method,
                    income ? r.discount() : null, income ? r.shipping() : null, notes, lines);
            BigDecimal total = SaleRules.total(draft);
            if (total.signum() < 0) {
                throw new ValidationException("discount", "El descuento supera el total");
            }
            Sale saved = sales.add(draft);
            for (SaleLine l : lines) {
                movements.add(new StockMovement(null, day, branch.id(), l.productionId(), l.packId(), -l.quantity(),
                        MovementType.SALE, saved.id(), null));
            }
            List<Payment> paid = new ArrayList<>();
            if (total.signum() > 0) {
                paid.add(payments.add(new Payment(null, saved.id(), day, total, method)));
            }
            if (paysWithPacks) {
                BigDecimal count = BigDecimal.valueOf(SaleRules.packs(lines));
                String expenseType = blankToNull(r.expenseType());
                expenses.add(new Expense(null, day, expenseType == null ? "personal" : expenseType, notes, packsValue,
                        count, packsValue.divide(count, Decimals.MC).setScale(2, RoundingMode.HALF_UP),
                        PaymentMethod.PACKS, true, null, null, saved.id(), null));
            }
            return new Views().of(saved, paid);
        });
    }

    @Override
    public void annul(long id, long actorId) {
        tx.run(() -> {
            stockLock.lock();
            Sale s = find(id);
            monthCloses.requireOpen(s.date());
            movements.findBySale(id).forEach(m -> movements.softDelete(m.id(), actorId));
            payments.softDeleteBySale(id, actorId);
            expenses.findBySale(id).ifPresent(e -> expenses.softDelete(e.id(), actorId));
            sales.softDelete(id, actorId);
        });
    }

    private Sale find(long id) {
        return sales.findById(id).orElseThrow(() -> new ResourceNotFoundException("Venta no encontrada"));
    }

    private static boolean matches(Sale s, SaleFilter f) {
        return f == null || ((f.branchId() == null || f.branchId() == s.branchId())
                && (f.type() == null || f.type().equals(s.type().code()))
                && (f.customerId() == null || f.customerId().equals(s.customerId()))
                && (f.from() == null || !s.date().isBefore(f.from()))
                && (f.to() == null || !s.date().isAfter(f.to())));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private BigDecimal packPrice(Pack p) {
        return p.price() != null ? p.price() : settings.require().packPrice();
    }

    private String packName(long id) {
        return packs.findById(id).map(Pack::name).orElse(UNKNOWN);
    }

    private String lotCode(long id) {
        return productions.findById(id).map(Production::code).orElse(UNKNOWN);
    }

    /** Builds views for just these sales; names and costs are looked up once and memoized. */
    private final class Views {
        private final Map<Long, String> packNames = new HashMap<>();
        private final Map<Long, String> customerNames = new HashMap<>();
        private final Map<Long, Production> lots = new HashMap<>();
        private final Map<Long, Branch> branchById = new HashMap<>();
        private Settings s;
        private SaleProfits profits;

        SaleView of(Sale sale, List<Payment> paid) {
            Branch branch = branchById.computeIfAbsent(sale.branchId(), id -> branches.findById(id).orElse(null));
            String customer = sale.customerId() == null ? null
                    : customerNames.computeIfAbsent(sale.customerId(), id -> customers.findById(id)
                    .map(Customer::fullName).orElse(UNKNOWN));
            List<SaleView.LineView> lines = sale.lines().stream()
                    .map(l -> new SaleView.LineView(l.productionId(), lot(l.productionId())
                            .map(Production::code).orElse(UNKNOWN), l.packId(),
                            packNames.computeIfAbsent(l.packId(), SaleService.this::packName), l.quantity(),
                            l.unitPrice())).toList();
            BigDecimal profit = profits().of(sale);
            return new SaleView(sale, branch == null ? UNKNOWN : branch.name(), customer, SaleRules.packs(sale.lines()),
                    lines, SaleRules.total(sale), profit, paid);
        }

        private Optional<Production> lot(long id) {
            return Optional.ofNullable(lots.computeIfAbsent(id, i -> productions.findById(i).orElse(null)));
        }

        private Settings settings() {
            if (s == null) {
                s = settings.require();
            }
            return s;
        }

        private SaleProfits profits() {
            if (profits == null) {
                // closes looked up per lot month (memoized there), not all loaded
                profits = new SaleProfits(productions.findAll(), supplies.findAll(), expenses.findAll(), monthCloses::find,
                        settings(), branches.findAll());
            }
            return profits;
        }
    }
}
