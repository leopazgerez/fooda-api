package com.fooda.api.production.application.service;

import com.fooda.api.core.application.port.out.ExpenseRepository;
import com.fooda.api.core.application.port.out.MonthCloseRepository;
import com.fooda.api.core.application.port.out.ProductionRepository;
import com.fooda.api.core.application.port.out.PurchaseRepository;
import com.fooda.api.core.application.port.out.SettingsRepository;
import com.fooda.api.core.application.port.out.SupplierRepository;
import com.fooda.api.core.application.port.out.SupplyRepository;
import com.fooda.api.core.domain.catalog.Costs;
import com.fooda.api.core.domain.catalog.PackagingType;
import com.fooda.api.core.domain.catalog.Settings;
import com.fooda.api.core.domain.catalog.Supplier;
import com.fooda.api.core.domain.catalog.Supply;
import com.fooda.api.core.domain.catalog.SupplyPrice;
import com.fooda.api.core.domain.expense.Expense;
import com.fooda.api.core.domain.expense.PaymentMethod;
import com.fooda.api.core.domain.production.Production;
import com.fooda.api.core.domain.production.ProductionRules;
import com.fooda.api.core.domain.production.ProductionStatus;
import com.fooda.api.core.domain.production.Purchase;
import com.fooda.api.core.domain.production.PurchaseLine;
import com.fooda.api.core.domain.production.PurchaseStatus;
import com.fooda.api.production.application.port.in.BoughtPurchase;
import com.fooda.api.production.application.port.in.PackagingStatus;
import com.fooda.api.production.application.port.in.PriceAlert;
import com.fooda.api.production.application.port.in.PurchaseUseCase;
import com.fooda.api.production.application.port.in.PurchaseView;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.Listing;
import com.fooda.api.shared.application.Page;
import com.fooda.api.shared.application.exception.ResourceNotFoundException;
import com.fooda.api.shared.application.port.out.Transactions;
import com.fooda.api.shared.domain.ConflictException;
import com.fooda.api.shared.domain.Decimals;
import com.fooda.api.shared.domain.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PurchaseService implements PurchaseUseCase {

    private static final Map<String, Comparator<PurchaseView>> SORTABLE = Map.of(
            "id", Comparator.comparingLong(v -> v.purchase().id()),
            "supplier", Comparator.comparing(PurchaseView::supplier, String.CASE_INSENSITIVE_ORDER),
            "production", Comparator.comparing(PurchaseView::production,
                    Comparator.nullsFirst(ProductionRules.CODE_ORDER)));

    private final SupplyRepository supplies;
    private final SupplierRepository suppliers;
    private final SettingsRepository settings;
    private final ProductionRepository productions;
    private final PurchaseRepository purchases;
    private final ExpenseRepository expenses;
    private final MonthCloseRepository monthCloses;
    private final PurchasePlanner planner;
    private final BusinessCalendar calendar;
    private final Transactions tx;

    public PurchaseService(SupplyRepository supplies, SupplierRepository suppliers, SettingsRepository settings,
                           ProductionRepository productions, PurchaseRepository purchases,
                           ExpenseRepository expenses, MonthCloseRepository monthCloses, PurchasePlanner planner,
                           BusinessCalendar calendar, Transactions tx) {
        this.supplies = supplies;
        this.suppliers = suppliers;
        this.settings = settings;
        this.productions = productions;
        this.purchases = purchases;
        this.expenses = expenses;
        this.monthCloses = monthCloses;
        this.planner = planner;
        this.calendar = calendar;
        this.tx = tx;
    }

    @Override
    public Page<PurchaseView> list(ListQuery q, Long productionId) {
        List<PurchaseView> all = (productionId == null ? purchases.findAll() : purchases.findByProduction(productionId))
                .stream().map(viewer()).toList();
        return Listing.page(all, q, SORTABLE, "id", "desc", Comparator.comparingLong(v -> v.purchase().id()));
    }

    @Override
    public PurchaseView get(long id) {
        return viewer().apply(purchases.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Compra no encontrada")));
    }

    @Override
    public BoughtPurchase buy(long id, LocalDate date, List<PurchaseLine> lines) {
        return tx.get(() -> {
            Purchase p = purchases.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Compra no encontrada"));
            if (p.status() == PurchaseStatus.BOUGHT) {
                throw new ConflictException("status", "Esta compra ya está registrada");
            }
            return register(p, date, lines);
        });
    }

    @Override
    public BoughtPurchase buyLoose(long supplierId, LocalDate date, List<PurchaseLine> lines) {
        return tx.get(() -> {
            suppliers.findById(supplierId).orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado"));
            return register(new Purchase(null, null, supplierId, PurchaseStatus.PENDING, null, null, null, null),
                    date, lines);
        });
    }

    @Override
    public void annul(long id, long actorId) {
        tx.run(() -> {
            Purchase p = purchases.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Compra no encontrada"));
            if (p.status() != PurchaseStatus.BOUGHT) {
                throw new ConflictException("status", "La compra no está registrada");
            }
            monthCloses.requireOpen(p.date());
            expenses.findByPurchase(id).ifPresent(e -> expenses.softDelete(e.id(), actorId));
            supplies.softDeletePurchasePrices(id, actorId);
            if (p.productionId() == null) {
                purchases.softDelete(id, actorId);
                return;
            }
            purchases.save(new Purchase(p.id(), p.productionId(), p.supplierId(), PurchaseStatus.PENDING, null,
                    p.lines(), null, p.version()));
            Production prod = production(p.productionId());
            List<Purchase> stillBought = purchases.findByProduction(prod.id()).stream()
                    .filter(x -> x.status() == PurchaseStatus.BOUGHT).toList();
            Production saved = withPaidCosts(prod,
                    PaidCosts.recompute(prod, governing(prod, supplies.findAll()), stillBought));
            if (saved.isPlanned()) {
                planner.regenerate(saved, actorId);
            }
        });
    }

    @Override
    public PackagingStatus packaging() {
        Settings s = settings.require();
        List<Supply> live = supplies.findAll();
        List<PurchaseLine> boughtLines = purchases.findAll().stream()
                .filter(x -> x.status() == PurchaseStatus.BOUGHT).flatMap(x -> x.lines().stream()).toList();
        List<Production> closed = productions.findAll().stream()
                .filter(p -> p.status() == ProductionStatus.CLOSED && !p.lines().isEmpty()).toList();
        int last = closed.stream().max(Comparator.comparing(Production::closedOn).thenComparing(Production::id))
                .map(Production::actualPacks).orElse(0);
        int consumedPacks = closed.stream().mapToInt(Production::actualPacks).sum();
        Function<PackagingType, PackagingStatus.Item> item = type -> {
            Long supplyId = live.stream().filter(x -> x.packagingType() == type).findFirst().map(Supply::id)
                    .orElse(null);
            int perPack = type == PackagingType.TRAY ? s.traysPerPack() : s.stickersPerPack();
            BigDecimal bought = boughtLines.stream().filter(l -> supplyId != null && l.supplyId() == supplyId)
                    .map(PurchaseLine::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal consumed = BigDecimal.valueOf((long) consumedPacks * perPack);
            BigDecimal need = BigDecimal.valueOf((long) last * perPack);
            BigDecimal stock = bought.subtract(consumed);
            return new PackagingStatus.Item(supplyId, bought, consumed, stock, need, stock.compareTo(need) >= 0);
        };
        return new PackagingStatus(item.apply(PackagingType.TRAY), item.apply(PackagingType.STICKER));
    }

    /** Port de comprar; must run inside the caller's transaction. A purchase without id is inserted. */
    private BoughtPurchase register(Purchase p, LocalDate date, List<PurchaseLine> lines) {
        LocalDate day = calendar.orToday(date);
        if (lines == null || lines.isEmpty()) {
            throw new ValidationException("lines", "La compra necesita al menos un ítem");
        }
        List<Supply> live = supplies.findAll();
        Map<Long, Supply> liveById = live.stream().collect(Collectors.toMap(Supply::id, Function.identity()));
        for (PurchaseLine l : lines) {
            if (!liveById.containsKey(l.supplyId())) {
                throw new ResourceNotFoundException("Insumo no encontrado");
            }
        }
        monthCloses.requireOpen(day);
        Production prod = p.productionId() == null ? null : production(p.productionId());
        List<Supply> governing = governing(prod, live);
        BigDecimal total = ProductionRules.purchaseAmount(governing, lines);
        Purchase saved = purchases.save(new Purchase(p.id(), p.productionId(), p.supplierId(), PurchaseStatus.BOUGHT,
                day, lines, total, p.version()));
        List<PriceAlert> alerts = registerPrices(lines, day, liveById, saved.id());
        if (total.signum() > 0) {
            String detail = "Compra " + supplierName(p.supplierId()) + (prod == null ? "" : " · " + prod.code());
            expenses.add(new Expense(null, day, "mercadería", detail, total, null, null, PaymentMethod.TRANSFER, true,
                    null, saved.id(), null, null));
        }
        if (prod != null) {
            withPaidCosts(prod, PaidCosts.apply(prod.paidCosts(), prod, governing, lines));
        }
        return new BoughtPurchase(viewer().apply(saved), alerts);
    }

    /** Port de registrarPrecios: a price different from the one in force that day is added; big rises alert. */
    private List<PriceAlert> registerPrices(List<PurchaseLine> lines, LocalDate day, Map<Long, Supply> liveById,
                                            long purchaseId) {
        BigDecimal threshold = settings.require().priceRiseAlert();
        List<SupplyPrice> prices = new ArrayList<>(supplies.findAllPrices());
        List<PriceAlert> alerts = new ArrayList<>();
        for (PurchaseLine l : lines) {
            SupplyPrice previous = Costs.latestPrice(l.supplyId(), prices, day).orElse(null);
            if (previous != null && previous.packagePrice().compareTo(l.packagePrice()) == 0) {
                continue;
            }
            // ponytail: a later same-day purchase at this price adds no row of its own, so annulling this one
            // drops the price for both; link prices to every matching purchase if that ever matters.
            prices.add(supplies.addPurchasePrice(new SupplyPrice(null, l.supplyId(), day, l.packagePrice()), purchaseId));
            if (previous != null && previous.packagePrice().signum() > 0) {
                BigDecimal rise = l.packagePrice().divide(previous.packagePrice(), Decimals.MC)
                        .subtract(BigDecimal.ONE);
                if (threshold != null && rise.compareTo(threshold) > 0) {
                    alerts.add(new PriceAlert(l.supplyId(), liveById.get(l.supplyId()).name(),
                            previous.packagePrice(), l.packagePrice(), rise));
                }
            }
        }
        return alerts;
    }

    /** Live supplies overridden by the production's frozen ones: one created after planning keeps its content. */
    private static List<Supply> governing(Production prod, List<Supply> live) {
        if (prod == null) {
            return live;
        }
        Map<Long, Supply> byId = new LinkedHashMap<>();
        live.forEach(s -> byId.put(s.id(), s));
        ProductionRules.suppliesOf(prod, live).forEach(s -> byId.put(s.id(), s));
        return List.copyOf(byId.values());
    }

    private Production withPaidCosts(Production p, Map<Long, BigDecimal> paid) {
        return productions.save(new Production(p.id(), p.code(), p.number(), p.status(), p.start(), p.closedOn(),
                p.lines(), p.snapshot(), paid, p.leftovers(), p.version()));
    }

    private Production production(long id) {
        return productions.findById(id).orElseThrow(() -> new ResourceNotFoundException("Producción no encontrada"));
    }

    private String supplierName(long id) {
        return suppliers.findById(id).map(Supplier::name).orElse("—");
    }

    /** Loads what every view shares once and returns a purchase → view mapper (port of compraVista). */
    private Function<Purchase, PurchaseView> viewer() {
        List<Supply> live = supplies.findAll();
        Map<Long, String> supplierNames = suppliers.findAll().stream()
                .collect(Collectors.toMap(Supplier::id, Supplier::name));
        Map<Long, Production> prods = productions.findAll().stream()
                .collect(Collectors.toMap(Production::id, Function.identity()));
        return p -> {
            Production prod = p.productionId() == null ? null : prods.get(p.productionId());
            List<Supply> governing = governing(prod, live);
            Map<Long, Supply> byId = new HashMap<>();
            governing.forEach(s -> byId.put(s.id(), s));
            List<PurchaseView.LineView> lines = p.lines().stream().map(l -> {
                Supply s = byId.get(l.supplyId());
                return new PurchaseView.LineView(l.supplyId(), l.quantity(), l.packagePrice(),
                        s == null ? "—" : s.name(), s == null ? null : s.unit(), s == null ? null : s.content());
            }).toList();
            BigDecimal total = p.total() != null ? p.total() : ProductionRules.purchaseAmount(governing, p.lines());
            return new PurchaseView(p, supplierNames.getOrDefault(p.supplierId(), "—"),
                    prod == null ? null : prod.code(), total, lines);
        };
    }
}
