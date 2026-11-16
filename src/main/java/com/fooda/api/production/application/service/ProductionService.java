package com.fooda.api.production.application.service;

import com.fooda.api.core.application.port.out.BranchRepository;
import com.fooda.api.core.application.port.out.DishRepository;
import com.fooda.api.core.application.port.out.ExpenseRepository;
import com.fooda.api.core.application.port.out.MonthCloseRepository;
import com.fooda.api.core.application.port.out.PackRepository;
import com.fooda.api.core.application.port.out.ProductionRepository;
import com.fooda.api.core.application.port.out.PurchaseRepository;
import com.fooda.api.core.application.port.out.SettingsRepository;
import com.fooda.api.core.application.port.out.SoldPacks;
import com.fooda.api.core.application.port.out.StockLock;
import com.fooda.api.core.application.port.out.StockMovementRepository;
import com.fooda.api.core.application.port.out.SupplierRepository;
import com.fooda.api.core.application.port.out.SupplyRepository;
import com.fooda.api.core.application.port.out.TransferRepository;
import com.fooda.api.core.domain.catalog.Branch;
import com.fooda.api.core.domain.catalog.Pack;
import com.fooda.api.core.domain.catalog.Settings;
import com.fooda.api.core.domain.catalog.Supply;
import com.fooda.api.core.domain.expense.Expense;
import com.fooda.api.core.domain.finance.AppliedOverhead;
import com.fooda.api.core.domain.finance.Finance;
import com.fooda.api.core.domain.production.PackCostInProduction;
import com.fooda.api.core.domain.production.Production;
import com.fooda.api.core.domain.production.ProductionLine;
import com.fooda.api.core.domain.production.ProductionRules;
import com.fooda.api.core.domain.production.ProductionSnapshot;
import com.fooda.api.core.domain.production.ProductionStatus;
import com.fooda.api.core.domain.production.Purchase;
import com.fooda.api.core.domain.production.PurchaseStatus;
import com.fooda.api.core.domain.production.SnapshotPack;
import com.fooda.api.core.domain.stock.MovementType;
import com.fooda.api.core.domain.stock.StockMovement;
import com.fooda.api.core.domain.stock.Transfer;
import com.fooda.api.core.domain.stock.TransferLine;
import com.fooda.api.production.application.port.in.CloseRequest;
import com.fooda.api.production.application.port.in.ProductionDetail;
import com.fooda.api.production.application.port.in.ProductionSummary;
import com.fooda.api.production.application.port.in.ProductionUseCase;
import com.fooda.api.production.application.port.in.RecipeBookMenu;
import com.fooda.api.production.application.port.in.RecipeBookPack;
import com.fooda.api.production.application.port.in.ShoppingListGroup;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ProductionService implements ProductionUseCase {

    private static final String LINES = "lines";
    private static final String DISTRIBUTION = "distribution";
    private static final Map<String, Comparator<ProductionSummary>> SORTABLE = Map.of(
            "start", Comparator.comparing(ProductionSummary::start),
            "code", Comparator.comparing(ProductionSummary::code, ProductionRules.CODE_ORDER),
            "id", Comparator.comparingLong(ProductionSummary::id));

    private final ProductionRepository productions;
    private final PurchaseRepository purchases;
    private final PackRepository packs;
    private final DishRepository dishes;
    private final SupplyRepository supplies;
    private final SupplierRepository suppliers;
    private final SettingsRepository settings;
    private final ExpenseRepository expenses;
    private final MonthCloseRepository monthCloses;
    private final SoldPacks soldPacks;
    private final TransferRepository transfers;
    private final StockMovementRepository movements;
    private final BranchRepository branches;
    private final StockLock stockLock;
    private final PurchasePlanner planner;
    private final BusinessCalendar calendar;
    private final Transactions tx;

    public ProductionService(ProductionRepository productions, PurchaseRepository purchases, PackRepository packs,
                             DishRepository dishes, SupplyRepository supplies, SupplierRepository suppliers,
                             SettingsRepository settings, ExpenseRepository expenses,
                             MonthCloseRepository monthCloses, SoldPacks soldPacks, TransferRepository transfers,
                             StockMovementRepository movements, BranchRepository branches, StockLock stockLock,
                             PurchasePlanner planner, BusinessCalendar calendar, Transactions tx) {
        this.productions = productions;
        this.purchases = purchases;
        this.packs = packs;
        this.dishes = dishes;
        this.supplies = supplies;
        this.suppliers = suppliers;
        this.settings = settings;
        this.expenses = expenses;
        this.monthCloses = monthCloses;
        this.soldPacks = soldPacks;
        this.transfers = transfers;
        this.movements = movements;
        this.branches = branches;
        this.stockLock = stockLock;
        this.planner = planner;
        this.calendar = calendar;
        this.tx = tx;
    }

    @Override
    public Page<ProductionSummary> list(ListQuery q) {
        List<ProductionSummary> all = productions.findAll().stream().map(summarizer()).toList();
        return Listing.page(all, q, SORTABLE, "start", "desc", Comparator.comparingLong(ProductionSummary::id));
    }

    @Override
    public ProductionDetail get(long id) {
        Production p = find(id);
        return new ProductionDetail(summarizer().apply(p), p.leftovers(), transfers.findContainingProduction(id));
    }

    @Override
    public ProductionSummary plan(List<PlanLine> lines, LocalDate start, long actorId) {
        return tx.get(() -> {
            if (lines == null || lines.isEmpty()) {
                throw new ValidationException(LINES, "Elegí al menos un pack");
            }
            for (PlanLine l : lines) {
                packs.findById(l.packId()).orElseThrow(() -> new ResourceNotFoundException("Pack no encontrado"));
                if (l.planned() < 0) {
                    throw new ValidationException(LINES, "Las cantidades deben ser enteros ≥ 0");
                }
            }
            if (new HashSet<>(lines.stream().map(PlanLine::packId).toList()).size() != lines.size()) {
                throw new ValidationException(LINES, "Cada pack va una sola vez en la producción");
            }
            if (lines.stream().noneMatch(l -> l.planned() > 0)) {
                throw new ValidationException(LINES, "Indicá cuántos packs vas a producir");
            }
            Settings s = liveSettings();
            LocalDate day = calendar.orToday(start);
            List<ProductionLine> kept = lines.stream().filter(l -> l.planned() > 0)
                    .map(l -> new ProductionLine(l.packId(), l.planned(), null)).toList();
            int number = productions.maxNumber(YearMonth.from(day)) + 1;
            ProductionSnapshot snapshot = ProductionRules.takeSnapshot(
                    kept.stream().map(ProductionLine::packId).toList(), packs.findAllVersions(), dishes.findAll(),
                    supplies.findAll(), supplies.findAllPrices(), s, day);
            Production saved = productions.save(new Production(null, ProductionRules.lotCode(number, day), number,
                    ProductionStatus.PLANNED, day, null, kept, snapshot, null, null, null));
            planner.regenerate(saved, actorId);
            return summarizer().apply(saved);
        });
    }

    @Override
    public List<ShoppingListGroup> shoppingList(long id) {
        Production p = find(id);
        BigDecimal packPrice = liveSettings().packPrice();
        List<Purchase> ofProduction = purchases.findByProduction(id);
        return ProductionRules.shoppingList(p, supplies.findAll(), suppliers.findAll()).stream().map(g -> {
            Purchase purchase = ofProduction.stream().filter(x -> x.supplierId() == g.supplier().id())
                    .findFirst().orElse(null);
            return new ShoppingListGroup(g.supplier().id(), g.supplier().name(), g.items(), g.total(),
                    Finance.inPacks(g.total(), packPrice), purchase == null ? null : purchase.id(),
                    purchase == null ? PurchaseStatus.PENDING : purchase.status());
        }).toList();
    }

    @Override
    public List<RecipeBookPack> recipeBook(long id) {
        Production p = find(id);
        Map<Long, String> names = packNames();
        return ProductionRules.recipeBook(p, supplies.findAll()).stream()
                .map(r -> new RecipeBookPack(r.packId(), names.getOrDefault(r.packId(), "—"), r.quantity(),
                        r.menus().stream().map(m -> new RecipeBookMenu(m.number(), m.dish(), m.ingredients(),
                                m.sharedWith() == null ? null : new RecipeBookMenu.SharedWith(m.sharedWith().packId(),
                                        names.getOrDefault(m.sharedWith().packId(), "—"), m.sharedWith().quantity()),
                                m.combinedTotal())).toList()))
                .toList();
    }

    @Override
    public ProductionSummary setLeftovers(long id, Map<Long, BigDecimal> leftovers, Long version, long actorId) {
        return tx.get(() -> {
            Production p = planned(id);
            Map<Long, BigDecimal> values = leftovers == null ? Map.of() : leftovers;
            values.forEach((supplyId, qty) -> {
                supplies.findById(supplyId).orElseThrow(() -> new ResourceNotFoundException("Insumo no encontrado"));
                if (qty == null || qty.signum() < 0) {
                    throw new ValidationException("leftovers", "Los sobrantes deben ser ≥ 0");
                }
            });
            Production saved = productions.save(new Production(p.id(), p.code(), p.number(), p.status(), p.start(),
                    p.closedOn(), p.lines(), p.snapshot(), p.paidCosts(), values,
                    version != null ? version : p.version()));
            planner.regenerate(saved, actorId);
            return summarizer().apply(saved);
        });
    }

    @Override
    public void annul(long id, long actorId) {
        tx.run(() -> {
            planned(id);
            List<Purchase> ofProduction = purchases.findByProduction(id);
            if (ofProduction.stream().anyMatch(x -> x.status() == PurchaseStatus.BOUGHT)) {
                throw new ConflictException(null, "Tiene compras registradas; anulalas primero");
            }
            ofProduction.forEach(x -> purchases.softDelete(x.id(), actorId));
            productions.softDelete(id, actorId);
        });
    }

    @Override
    public ProductionSummary close(long id, CloseRequest r) {
        return tx.get(() -> {
            stockLock.lock();
            Production p = planned(id);
            LocalDate day = calendar.orToday(r.date());
            if (day.isBefore(p.start())) {
                throw new ValidationException("date", "La fecha de cierre no puede ser anterior al inicio");
            }
            if (day.isAfter(calendar.today())) {
                throw new ValidationException("date", "La fecha de cierre no puede ser futura");
            }
            monthCloses.requireOpen(day);
            Map<Long, Integer> actuals = new HashMap<>();
            for (CloseRequest.Actual a : r.lines()) {
                inProduction(p, a.packId(), LINES);
                if (a.actual() < 0) {
                    throw new ValidationException(LINES, "Las cantidades reales deben ser enteros ≥ 0");
                }
                if (actuals.putIfAbsent(a.packId(), a.actual()) != null) {
                    throw new ValidationException(LINES, "Cada pack va una sola vez en el cierre");
                }
            }
            Branch kitchen = branches.findKitchen()
                    .orElseThrow(() -> new ValidationException("branch", "No hay una sede marcada como cocina"));
            List<CloseRequest.Distribution> distribution =
                    r.distribution().stream().filter(d -> d.quantity() != 0).toList();
            for (CloseRequest.Distribution d : distribution) {
                branches.findById(d.branchId()).orElseThrow(() -> new ResourceNotFoundException("Sede no encontrada"));
                if (d.branchId() == kitchen.id()) {
                    throw new ValidationException(DISTRIBUTION, "El reparto es hacia otras sedes");
                }
                inProduction(p, d.packId(), DISTRIBUTION);
                if (d.quantity() < 0) {
                    throw new ValidationException(DISTRIBUTION, "El reparto debe ser en enteros ≥ 0");
                }
            }
            List<ProductionLine> lines = p.lines().stream().map(l -> new ProductionLine(l.packId(), l.planned(),
                    actuals.getOrDefault(l.packId(), l.planned()))).toList();
            for (ProductionLine l : lines) {
                int sent = distribution.stream().filter(d -> d.packId() == l.packId())
                        .mapToInt(CloseRequest.Distribution::quantity).sum();
                if (sent > l.actual()) {
                    throw new ValidationException(DISTRIBUTION, "Se reparten %d de %s pero se produjeron %d"
                            .formatted(sent, packName(l.packId()), l.actual()));
                }
            }
            Production saved = productions.save(new Production(p.id(), p.code(), p.number(), ProductionStatus.CLOSED,
                    p.start(), day, lines, p.snapshot(), p.paidCosts(), p.leftovers(), p.version()));
            for (ProductionLine l : lines) {
                if (l.actual() > 0) {
                    movements.add(new StockMovement(null, day, kitchen.id(), id, l.packId(), l.actual(),
                            MovementType.PRODUCTION, id, null));
                }
            }
            Map<Long, List<TransferLine>> byBranch = new LinkedHashMap<>();
            distribution.forEach(d -> byBranch.computeIfAbsent(d.branchId(), k -> new ArrayList<>())
                    .add(new TransferLine(id, d.packId(), d.quantity())));
            byBranch.forEach((branchId, transferLines) -> {
                Transfer t = transfers.add(new Transfer(null, day, kitchen.id(), branchId,
                        "Reparto al cerrar " + p.code(), id, transferLines));
                for (TransferLine l : t.lines()) {
                    movements.add(new StockMovement(null, day, kitchen.id(), id, l.packId(), -l.quantity(),
                            MovementType.TRANSFER_OUT, t.id(), null));
                    movements.add(new StockMovement(null, day, branchId, id, l.packId(), l.quantity(),
                            MovementType.TRANSFER_IN, t.id(), null));
                }
            });
            return summarizer().apply(saved);
        });
    }

    @Override
    public ProductionSummary reopen(long id, long actorId) {
        return tx.get(() -> {
            stockLock.lock();
            Production p = find(id);
            if (p.status() != ProductionStatus.CLOSED) {
                throw new ConflictException("status", "La producción no está cerrada");
            }
            monthCloses.requireOpen(p.closedOn());
            if (p.isInitial()) {
                throw new ConflictException("status", "El lote inicial no se reabre");
            }
            List<Transfer> distribution = transfers.findBySourceProduction(id);
            Set<Long> transferIds = distribution.stream().map(Transfer::id).collect(Collectors.toSet());
            List<StockMovement> lot = movements.findByProduction(id);
            if (!lot.stream().allMatch(m -> fromClosing(m, id, transferIds))) {
                throw new ConflictException(null, "Los packs de este lote ya se movieron; no se puede reabrir");
            }
            lot.forEach(m -> movements.softDelete(m.id(), actorId));
            distribution.forEach(t -> transfers.softDelete(t.id(), actorId));
            List<ProductionLine> lines = p.lines().stream()
                    .map(l -> new ProductionLine(l.packId(), l.planned(), null)).toList();
            Production saved = productions.save(new Production(p.id(), p.code(), p.number(), ProductionStatus.PLANNED,
                    p.start(), null, lines, p.snapshot(), p.paidCosts(), p.leftovers(), p.version()));
            planner.regenerate(saved, actorId);
            return summarizer().apply(saved);
        });
    }

    /** The closing's own movement: its PRODUCTION entry, or one leg of a distribution transfer. */
    private static boolean fromClosing(StockMovement m, long id, Set<Long> distributionIds) {
        return switch (m.type()) {
            case PRODUCTION -> m.refId() != null && m.refId() == id;
            case TRANSFER_OUT, TRANSFER_IN -> distributionIds.contains(m.refId());
            default -> false;
        };
    }

    private void inProduction(Production p, long packId, String field) {
        if (p.lines().stream().noneMatch(l -> l.packId() == packId)) {
            throw new ValidationException(field, packName(packId) + " no está en esta producción");
        }
    }

    private String packName(long packId) {
        return packs.findById(packId).map(Pack::name).orElse("—");
    }

    private Production find(long id) {
        return productions.findById(id).orElseThrow(() -> new ResourceNotFoundException("Producción no encontrada"));
    }

    private Production planned(long id) {
        Production p = find(id);
        if (!p.isPlanned()) {
            throw new ConflictException("status", "La producción ya está cerrada");
        }
        return p;
    }

    private Settings liveSettings() {
        return settings.find()
                .orElseThrow(() -> new ResourceNotFoundException("Todavía no se cargaron los parámetros"));
    }

    private Map<Long, String> packNames() {
        return packs.findAll().stream().collect(Collectors.toMap(Pack::id, Pack::name));
    }

    /** Loads what every summary shares once and returns a production → summary mapper (port of produccionResumen). */
    private Function<Production, ProductionSummary> summarizer() {
        Settings s = liveSettings();
        List<Production> all = productions.findAll();
        List<Expense> allExpenses = expenses.findAll();
        List<Supply> live = supplies.findAll();
        Map<Long, Integer> sold = soldPacks.soldByProduction();
        Map<Long, String> names = packNames();
        return p -> {
            YearMonth month = YearMonth.from(p.closedOn() != null ? p.closedOn() : p.start());
            AppliedOverhead overhead = Finance.applicable(month, monthCloses.find(month), allExpenses, all, s);
            BigDecimal estimated = BigDecimal.ZERO;
            BigDecimal actual = BigDecimal.ZERO;
            List<ProductionSummary.PackLine> lines = new ArrayList<>();
            for (ProductionLine l : p.lines()) {
                PackCostInProduction c = ProductionRules.packCost(p, l.packId(), live);
                BigDecimal quantity = BigDecimal.valueOf(l.actual() != null ? l.actual() : l.planned());
                estimated = estimated.add(quantity.multiply(c.estimated().add(overhead.value())));
                actual = actual.add(quantity.multiply(c.actual().add(overhead.value())));
                lines.add(new ProductionSummary.PackLine(l.packId(), names.getOrDefault(l.packId(), "—"), l.planned(),
                        l.actual(), p.snapshotPack(l.packId()).map(SnapshotPack::versionId).orElse(null),
                        c.estimated(), c.actual(), c.actual().add(overhead.value())));
            }
            int total = p.packs();
            int soldCount = sold.getOrDefault(p.id(), 0);
            BigDecimal ratio = total > 0
                    ? BigDecimal.valueOf(soldCount).divide(BigDecimal.valueOf(total), Decimals.MC) : BigDecimal.ZERO;
            return new ProductionSummary(p.id(), p.code(), p.number(), p.status(), p.start(), p.closedOn(), total,
                    estimated, actual, soldCount, ratio, overhead, lines,
                    Finance.payback(actual, s.packPrice(), soldCount), p.version());
        };
    }
}
