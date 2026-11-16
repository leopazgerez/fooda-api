package com.fooda.api.production.application.service;

import com.fooda.api.catalog.fakes.*;
import com.fooda.api.core.domain.catalog.*;
import com.fooda.api.core.domain.expense.Expense;
import com.fooda.api.core.domain.expense.PaymentMethod;
import com.fooda.api.core.domain.finance.MonthClose;
import com.fooda.api.core.domain.production.*;
import com.fooda.api.production.application.port.in.BoughtPurchase;
import com.fooda.api.production.application.port.in.PackagingStatus;
import com.fooda.api.production.application.port.in.ProductionSummary;
import com.fooda.api.production.application.port.in.ProductionUseCase;
import com.fooda.api.production.application.port.in.ProductionUseCase.PlanLine;
import com.fooda.api.production.application.port.in.PurchaseUseCase;
import com.fooda.api.production.application.port.in.PurchaseView;
import com.fooda.api.production.fakes.*;
import com.fooda.api.shared.DirectTransactions;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.exception.ResourceNotFoundException;
import com.fooda.api.shared.domain.ConflictException;
import com.fooda.api.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PurchaseServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);
    private static final long ACTOR = 1L;
    private static final long POLLO = 1, ROJO = 2, VERDE = 3, PIMIENTO = 4, BANDEJA = 5;

    private final InMemorySupplyRepository supplyRepo = new InMemorySupplyRepository();
    private final InMemorySupplierRepository supplierRepo = new InMemorySupplierRepository();
    private final InMemoryDishRepository dishRepo = new InMemoryDishRepository();
    private final InMemoryPackRepository packRepo = new InMemoryPackRepository();
    private final InMemorySettingsRepository settingsRepo = new InMemorySettingsRepository();
    private final InMemoryProductionRepository productionRepo = new InMemoryProductionRepository();
    private final InMemoryPurchaseRepository purchaseRepo = new InMemoryPurchaseRepository();
    private final InMemoryExpenseRepository expenseRepo = new InMemoryExpenseRepository();
    private final InMemoryBranchRepository branchRepo = new InMemoryBranchRepository();
    private final FixedMonthCloses closes = new FixedMonthCloses();
    private final NoopStockLock purchaseLock = new NoopStockLock();
    private final BusinessCalendar calendar =
            new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), ZoneOffset.UTC));
    private final DirectTransactions tx = new DirectTransactions();
    private final PurchasePlanner planner = new PurchasePlanner(purchaseRepo, supplyRepo, supplierRepo);
    private final ProductionUseCase productions = new ProductionService(productionRepo, purchaseRepo, packRepo,
            dishRepo, supplyRepo, supplierRepo, settingsRepo, expenseRepo, closes, new FixedSoldPacks(),
            new InMemoryTransferRepository(), new InMemoryStockMovementRepository(), branchRepo,
            new NoopStockLock(), planner, calendar, tx);
    private final PurchaseUseCase service = new PurchaseService(supplyRepo, supplierRepo, settingsRepo,
            productionRepo, purchaseRepo, expenseRepo, closes, purchaseLock, planner, calendar, tx);

    private final long butcher;
    private final long greens;
    private final long super_;
    private final long classic;

    {
        butcher = supplierRepo.save(new Supplier(null, "Carnicería", null, 1, null)).id();
        greens = supplierRepo.save(new Supplier(null, "Verdulería", null, 2, null)).id();
        super_ = supplierRepo.save(new Supplier(null, "Súper", null, 3, null)).id();
        supply(POLLO, "Pollo", Unit.G, "1000", butcher, null, null, "8000");
        supply(ROJO, "Pimiento rojo", Unit.G, "1000", greens, null, null, "6000");
        supply(VERDE, "Pimiento verde", Unit.G, "1000", greens, null, null, "4000");
        supply(PIMIENTO, "Pimiento", Unit.G, "1", greens, null,
                new Composite(CompositeRule.AVERAGE, List.of(ROJO, VERDE)), null);
        supply(BANDEJA, "Bandeja", Unit.U, "1", super_, PackagingType.TRAY, null, "750");
        long pollo = dishRepo.save(new Dish(null, "Pollo", List.of(line(POLLO, "300")), null)).id();
        long pimiento = dishRepo.save(new Dish(null, "Pimientos", List.of(line(PIMIENTO, "100")), null)).id();
        classic = packRepo.save(new Pack(null, 1, "Clásico", true, null, null)).id();
        packRepo.addVersion(new PackVersion(null, classic, LocalDate.of(2026, 10, 1), List.of(new MenuSlot(1, pollo),
                new MenuSlot(2, pimiento), new MenuSlot(3, pollo), new MenuSlot(4, pimiento),
                new MenuSlot(5, pollo))));
        settingsRepo.save(new Settings(new BigDecimal("45000"), null, null, 5, 1, new BigDecimal("1000"), 3, 30,
                new BigDecimal("0.15"), List.of(), List.of(Settings.FIXED_EXPENSE_TYPE), null));
    }

    private void supply(long id, String name, Unit unit, String content, long supplierId, PackagingType pt,
                        Composite composite, String price) {
        supplyRepo.save(new Supply(id, name, unit, supplierId, null, new BigDecimal(content), null, pt, composite,
                true, null));
        if (price != null) {
            supplyRepo.addPrice(new SupplyPrice(null, id, LocalDate.of(2026, 11, 1), new BigDecimal(price)));
        }
    }

    private static RecipeLine line(long supplyId, String quantity) {
        return new RecipeLine(supplyId, new BigDecimal(quantity), Unit.G, BigDecimal.ONE);
    }

    private static PurchaseLine pl(long supplyId, String quantity, String price) {
        return new PurchaseLine(supplyId, new BigDecimal(quantity), new BigDecimal(price));
    }

    private ProductionSummary plan() {
        return productions.plan(List.of(new PlanLine(classic, 10)), null, ACTOR);
    }

    private Purchase pendingOf(long productionId, long supplierId) {
        return purchaseRepo.findByProduction(productionId).stream().filter(p -> p.supplierId() == supplierId)
                .findFirst().orElseThrow();
    }

    @Test
    void buyWithChickenPlus20PercentAlertsAndRaisesActualCost() {
        ProductionSummary s = plan();
        Purchase pending = pendingOf(s.id(), butcher);
        List<PurchaseLine> lines = pending.lines().stream()
                .map(l -> new PurchaseLine(l.supplyId(), l.quantity(), l.packagePrice().multiply(new BigDecimal("1.2"))))
                .toList();

        BoughtPurchase r = service.buy(pending.id(), null, lines);

        assertThat(r.alerts()).singleElement().satisfies(a -> {
            assertThat(a.supplyId()).isEqualTo(POLLO);
            assertThat(a.supply()).isEqualTo("Pollo");
            assertThat(a.previous()).isEqualByComparingTo("8000");
            assertThat(a.current()).isEqualByComparingTo("9600");
            assertThat(a.rise()).isEqualByComparingTo("0.2");
        });
        PurchaseView v = r.purchase();
        assertThat(v.purchase().status()).isEqualTo(PurchaseStatus.BOUGHT);
        assertThat(v.purchase().date()).isEqualTo(TODAY);
        assertThat(v.supplier()).isEqualTo("Carnicería");
        assertThat(v.production()).isEqualTo(s.code());
        // 10 packs × 3 menús × 300 g = 9000 g → 9 × 9600
        assertThat(v.total()).isEqualByComparingTo("86400");
        assertThat(v.lines()).singleElement().satisfies(l -> {
            assertThat(l.supply()).isEqualTo("Pollo");
            assertThat(l.unit()).isEqualTo(Unit.G);
            assertThat(l.content()).isEqualByComparingTo("1000");
        });
        ProductionSummary after = productions.get(s.id()).summary();
        assertThat(after.actualCost()).isGreaterThan(after.estimatedCost());
        Expense e = expenseRepo.findByPurchase(pending.id()).orElseThrow();
        assertThat(e.type()).isEqualTo("mercadería");
        assertThat(e.detail()).isEqualTo("Compra Carnicería · " + s.code());
        assertThat(e.amount()).isEqualByComparingTo("86400");
        assertThat(e.method()).isEqualTo(PaymentMethod.TRANSFER);
        assertThat(e.paid()).isTrue();
        assertThat(e.date()).isEqualTo(TODAY);
    }

    @Test
    void buyKeepsPriceWhenUnchangedAndAddsWhenDifferent() {
        int before = supplyRepo.findAllPrices().size();
        BoughtPurchase same = service.buyLoose(greens, null, List.of(pl(ROJO, "1000", "6000.00")));
        assertThat(same.alerts()).isEmpty();
        assertThat(supplyRepo.findAllPrices()).hasSize(before);

        LocalDate day = LocalDate.of(2026, 11, 10);
        BoughtPurchase cheaper = service.buyLoose(super_, day, List.of(pl(BANDEJA, "100", "700")));
        assertThat(cheaper.alerts()).isEmpty();
        assertThat(supplyRepo.findPrices(BANDEJA)).last().satisfies(p -> {
            assertThat(p.date()).isEqualTo(day);
            assertThat(p.packagePrice()).isEqualByComparingTo("700");
        });

        // +10 % is under the 15 % threshold: new price, no alert
        BoughtPurchase small = service.buyLoose(greens, null, List.of(pl(VERDE, "1000", "4400")));
        assertThat(small.alerts()).isEmpty();
        assertThat(supplyRepo.findPrices(VERDE)).hasSize(2);
    }

    @Test
    void alreadyBoughtIs409() {
        ProductionSummary s = plan();
        Purchase pending = pendingOf(s.id(), butcher);
        service.buy(pending.id(), null, pending.lines());
        assertThatThrownBy(() -> service.buy(pending.id(), null, pending.lines()))
                .isInstanceOf(ConflictException.class).hasMessage("Esta compra ya está registrada")
                .extracting("field").isEqualTo("status");
    }

    @Test
    void buyValidations() {
        ProductionSummary s = plan();
        long id = pendingOf(s.id(), butcher).id();
        assertThatThrownBy(() -> service.buy(999, null, List.of(pl(POLLO, "1", "1"))))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Compra no encontrada");
        assertThatThrownBy(() -> service.buy(id, null, List.of()))
                .isInstanceOf(ValidationException.class).hasMessage("La compra necesita al menos un ítem")
                .extracting("field").isEqualTo("lines");
        assertThatThrownBy(() -> service.buy(id, null, null)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.buy(id, null, List.of(pl(99, "1", "1"))))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Insumo no encontrado");
        closes.closes.put(YearMonth.of(2026, 10), new MonthClose(YearMonth.of(2026, 10), BigDecimal.ONE, TODAY));
        assertThatThrownBy(() -> service.buy(id, LocalDate.of(2026, 10, 20), List.of(pl(POLLO, "1", "1"))))
                .isInstanceOf(ConflictException.class).hasMessage("Ese mes está cerrado")
                .extracting("field").isEqualTo("date");
        assertThatThrownBy(() -> service.buyLoose(999, null, List.of(pl(POLLO, "1", "1"))))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Proveedor no encontrado");
        assertThat(purchaseRepo.findById(id).orElseThrow().status()).isEqualTo(PurchaseStatus.PENDING);
        assertThat(expenseRepo.findAll()).isEmpty();
    }

    @Test
    void zeroTotalPurchaseCreatesNoExpense() {
        BoughtPurchase r = service.buyLoose(super_, null, List.of(pl(BANDEJA, "10", "0")));
        assertThat(r.purchase().total()).isEqualByComparingTo("0");
        assertThat(r.purchase().purchase().status()).isEqualTo(PurchaseStatus.BOUGHT);
        assertThat(expenseRepo.findAll()).isEmpty();
    }

    @Test
    void compositePaidCostRecalculated() {
        ProductionSummary s = plan();
        Purchase pending = pendingOf(s.id(), greens);

        service.buy(pending.id(), null, List.of(pl(ROJO, "1000", "9000")));

        Production p = productionRepo.findById(s.id()).orElseThrow();
        assertThat(p.paidCosts().get(ROJO)).isEqualByComparingTo("9");
        // promedio(rojo pagado 9, verde del snapshot 4)
        assertThat(p.paidCosts().get(PIMIENTO)).isEqualByComparingTo("6.5");
        assertThat(p.paidCosts()).doesNotContainKey(VERDE);
    }

    @Test
    void annulBoughtOfPlannedProductionRestoresPendingAndRecomputes() {
        ProductionSummary s = plan();
        Purchase chicken = pendingOf(s.id(), butcher);
        Purchase peppers = pendingOf(s.id(), greens);
        service.buy(chicken.id(), null, List.of(pl(POLLO, "9000", "9600")));
        service.buy(peppers.id(), null, List.of(pl(ROJO, "1000", "9000")));
        Expense chickenExpense = expenseRepo.findByPurchase(chicken.id()).orElseThrow();

        service.annul(peppers.id(), ACTOR);

        Purchase restored = purchaseRepo.findById(peppers.id()).orElseThrow();
        assertThat(restored.status()).isEqualTo(PurchaseStatus.PENDING);
        assertThat(restored.date()).isNull();
        assertThat(restored.total()).isNull();
        assertThat(restored.lines()).extracting(PurchaseLine::supplyId).containsExactlyInAnyOrder(ROJO, VERDE);
        assertThat(expenseRepo.findByPurchase(peppers.id())).isEmpty();
        assertThat(expenseRepo.findAll()).containsExactly(chickenExpense);
        Production p = productionRepo.findById(s.id()).orElseThrow();
        assertThat(p.paidCosts()).containsOnlyKeys(POLLO);
        assertThat(p.paidCosts().get(POLLO)).isEqualByComparingTo("9.6");
        assertThat(purchaseRepo.findByProduction(s.id())).extracting(Purchase::id)
                .containsExactlyInAnyOrder(chicken.id(), peppers.id());

        assertThatThrownBy(() -> service.annul(peppers.id(), ACTOR))
                .isInstanceOf(ConflictException.class).hasMessage("La compra no está registrada")
                .extracting("field").isEqualTo("status");
        closes.closes.put(YearMonth.of(2026, 11), new MonthClose(YearMonth.of(2026, 11), BigDecimal.ONE, TODAY));
        assertThatThrownBy(() -> service.annul(chicken.id(), ACTOR))
                .isInstanceOf(ConflictException.class).hasMessage("Ese mes está cerrado")
                .extracting("field").isEqualTo("date");
    }

    @Test
    void writesTakeTheStockLock() {
        long id = service.buyLoose(super_, null, List.of(pl(BANDEJA, "100", "7500"))).purchase().purchase().id();
        service.annul(id, ACTOR);
        assertThat(purchaseLock.calls).isEqualTo(2);
    }

    @Test
    void annulDropsThePricesThePurchaseAdded() {
        long id = service.buyLoose(super_, null, List.of(pl(BANDEJA, "100", "7500"))).purchase().purchase().id();
        assertThat(supplyRepo.findPrices(BANDEJA)).extracting(SupplyPrice::packagePrice)
                .usingElementComparator(BigDecimal::compareTo).containsExactly(new BigDecimal("750"), new BigDecimal("7500"));

        service.annul(id, ACTOR);

        assertThat(supplyRepo.findPrices(BANDEJA)).extracting(SupplyPrice::packagePrice)
                .usingElementComparator(BigDecimal::compareTo).containsExactly(new BigDecimal("750"));
    }

    @Test
    void looseBuyThenAnnulDeletesIt() {
        long id = service.buyLoose(super_, null, List.of(pl(BANDEJA, "100", "750"))).purchase().purchase().id();
        assertThat(expenseRepo.findByPurchase(id)).isPresent();

        service.annul(id, ACTOR);

        assertThat(purchaseRepo.findById(id)).isEmpty();
        assertThat(expenseRepo.findAll()).isEmpty();
    }

    @Test
    void packagingStatusFromBoughtAndClosed() {
        service.buyLoose(super_, null, List.of(pl(BANDEJA, "1500", "750")));
        Settings settings = settingsRepo.find().orElseThrow();
        productionRepo.save(new Production(null, "1 NOV 26", 1, ProductionStatus.CLOSED, LocalDate.of(2026, 11, 2),
                LocalDate.of(2026, 11, 3), List.of(new ProductionLine(classic, 100, 100)),
                ProductionSnapshot.empty(settings), null, null, null));

        PackagingStatus st = service.packaging();

        assertThat(st.tray().supplyId()).isEqualTo(BANDEJA);
        assertThat(st.tray().bought()).isEqualByComparingTo("1500");
        assertThat(st.tray().consumed()).isEqualByComparingTo("500");
        assertThat(st.tray().stock()).isEqualByComparingTo("1000");
        assertThat(st.tray().need()).isEqualByComparingTo("500");
        assertThat(st.tray().enough()).isTrue();
        assertThat(st.sticker().supplyId()).isNull();
        assertThat(st.sticker().bought()).isEqualByComparingTo("0");
        assertThat(st.sticker().consumed()).isEqualByComparingTo("100");
        assertThat(st.sticker().enough()).isFalse();
    }

    @Test
    void loosePurchaseDetailHasNoProductionCode() {
        BoughtPurchase r = service.buyLoose(super_, null, List.of(pl(BANDEJA, "100", "750")));
        assertThat(r.purchase().production()).isNull();
        assertThat(expenseRepo.findByPurchase(r.purchase().purchase().id()).orElseThrow().detail())
                .isEqualTo("Compra Súper");
    }

    @Test
    void listFiltersByProductionAndSortsByIdDesc() {
        ProductionSummary s = plan();
        long loose = service.buyLoose(super_, null, List.of(pl(BANDEJA, "100", "750"))).purchase().purchase().id();
        List<Long> ofProduction = purchaseRepo.findByProduction(s.id()).stream().map(Purchase::id).toList();

        assertThat(service.list(new ListQuery(0, 20, null, null), null).items())
                .extracting(v -> v.purchase().id()).startsWith(loose).hasSize(ofProduction.size() + 1);
        assertThat(service.list(new ListQuery(0, 20, null, null), s.id()).items())
                .extracting(v -> v.purchase().id()).containsExactlyInAnyOrderElementsOf(ofProduction)
                .allSatisfy(id -> assertThat(id).isNotEqualTo(loose));
        assertThat(service.list(new ListQuery(0, 20, "production", "asc"), null).items().get(0).production())
                .isNull();
        assertThat(service.list(new ListQuery(0, 20, null, null), s.id()).items())
                .allSatisfy(v -> assertThat(v.total()).isPositive());
        assertThatThrownBy(() -> service.list(new ListQuery(0, 20, "total", null), null))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void pendingListsOnlyPendingPurchasesById() {
        ProductionSummary s = plan();
        List<Long> pendingIds = purchaseRepo.findByProduction(s.id()).stream().map(Purchase::id).sorted().toList();
        long loose = service.buyLoose(super_, null, List.of(pl(BANDEJA, "100", "750"))).purchase().purchase().id();

        assertThat(service.pending()).extracting(v -> v.purchase().id()).containsExactlyElementsOf(pendingIds)
                .doesNotContain(loose);
        service.buy(pendingIds.get(0), null, List.of(pl(BANDEJA, "100", "750")));
        assertThat(service.pending()).extracting(v -> v.purchase().id()).doesNotContain(pendingIds.get(0));
    }

    @Test
    void supplyCreatedAfterPlanningUsesItsOwnContent() {
        ProductionSummary s = plan();
        supply(6, "Harina", Unit.G, "1000", greens, null, null, "5000");
        Purchase pending = pendingOf(s.id(), greens);

        BoughtPurchase r = service.buy(pending.id(), null, List.of(pl(6, "1000", "5000")));

        assertThat(r.purchase().total()).isEqualByComparingTo("5000");
        assertThat(r.purchase().lines()).singleElement()
                .satisfies(l -> assertThat(l.content()).isEqualByComparingTo("1000"));
        assertThat(expenseRepo.findByPurchase(pending.id()).orElseThrow().amount()).isEqualByComparingTo("5000");
        assertThat(productionRepo.findById(s.id()).orElseThrow().paidCosts().get(6L)).isEqualByComparingTo("5");
    }

    @Test
    void reopenRegeneratesPendingPurchasesFromTheShoppingList() {
        branchRepo.save(new Branch(null, "Cocina", "", 10, 1, 1, new Shipping(ShippingType.AMOUNT, BigDecimal.ZERO),
                false, true, null));
        ProductionSummary s = plan();
        Purchase peppers = pendingOf(s.id(), greens);
        service.buy(peppers.id(), null, List.of(pl(ROJO, "1000", "9000")));
        productions.close(s.id(), new com.fooda.api.production.application.port.in.CloseRequest(null, null, null));
        service.annul(peppers.id(), ACTOR);
        // closed: annul restores the bought lines as pending, without regenerating
        assertThat(pendingOf(s.id(), greens).lines()).extracting(PurchaseLine::supplyId).containsExactly(ROJO);

        productions.reopen(s.id(), ACTOR);

        var group = productions.shoppingList(s.id()).stream().filter(g -> g.supplierId() == greens)
                .findFirst().orElseThrow();
        Purchase regenerated = pendingOf(s.id(), greens);
        assertThat(regenerated.id()).isEqualTo(peppers.id());
        assertThat(regenerated.status()).isEqualTo(PurchaseStatus.PENDING);
        assertThat(regenerated.lines()).extracting(l -> List.of(l.supplyId(), l.quantity().stripTrailingZeros()))
                .containsExactlyInAnyOrderElementsOf(group.items().stream()
                        .map(i -> List.of(i.supplyId(), i.quantity().stripTrailingZeros())).toList());
    }

    @Test
    void getReturnsTheViewOr404() {
        ProductionSummary s = plan();
        Purchase pending = pendingOf(s.id(), butcher);
        PurchaseView v = service.get(pending.id());
        assertThat(v.purchase().id()).isEqualTo(pending.id());
        assertThat(v.production()).isEqualTo(s.code());
        assertThat(v.supplier()).isEqualTo("Carnicería");
        assertThatThrownBy(() -> service.get(999)).isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Compra no encontrada");
    }

    @Test
    void supplierSortIgnoresCase() {
        long lower = supplierRepo.save(new Supplier(null, "almacén", null, 9, null)).id();
        service.buyLoose(super_, null, List.of(pl(BANDEJA, "100", "750")));
        service.buyLoose(lower, null, List.of(pl(BANDEJA, "100", "750")));
        assertThat(service.list(new ListQuery(0, 20, "supplier", "asc"), null).items())
                .extracting(PurchaseView::supplier).containsExactly("almacén", "Súper");
    }
}
