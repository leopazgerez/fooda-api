package com.fooda.api.production.application.service;

import com.fooda.api.catalog.fakes.*;
import com.fooda.api.core.domain.catalog.*;
import com.fooda.api.core.domain.production.*;
import com.fooda.api.production.application.port.in.ProductionSummary;
import com.fooda.api.production.application.port.in.RecipeBookMenu;
import com.fooda.api.production.application.port.in.RecipeBookPack;
import com.fooda.api.production.application.port.in.ProductionUseCase;
import com.fooda.api.production.application.port.in.ProductionUseCase.PlanLine;
import com.fooda.api.production.application.port.in.ShoppingListGroup;
import com.fooda.api.production.fakes.*;
import com.fooda.api.shared.DirectTransactions;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.exception.ResourceNotFoundException;
import com.fooda.api.shared.domain.ConflictException;
import com.fooda.api.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);
    private static final long ACTOR = 1L;

    private final InMemorySupplyRepository supplyRepo = new InMemorySupplyRepository();
    private final InMemorySupplierRepository supplierRepo = new InMemorySupplierRepository();
    private final InMemoryDishRepository dishRepo = new InMemoryDishRepository();
    private final InMemoryPackRepository packRepo = new InMemoryPackRepository();
    private final InMemorySettingsRepository settingsRepo = new InMemorySettingsRepository();
    private final InMemoryProductionRepository productionRepo = new InMemoryProductionRepository();
    private final InMemoryPurchaseRepository purchaseRepo = new InMemoryPurchaseRepository();
    private final InMemoryExpenseRepository expenseRepo = new InMemoryExpenseRepository();
    private final InMemoryTransferRepository transferRepo = new InMemoryTransferRepository();
    private final FixedMonthCloses closes = new FixedMonthCloses();
    private final FixedSoldPacks sold = new FixedSoldPacks();
    private final BusinessCalendar calendar =
            new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), ZoneOffset.UTC));
    private final DirectTransactions tx = new DirectTransactions();
    private final PurchasePlanner planner = new PurchasePlanner(purchaseRepo, supplyRepo, supplierRepo);
    private final ProductionUseCase service = new ProductionService(productionRepo, purchaseRepo, packRepo, dishRepo,
            supplyRepo, supplierRepo, settingsRepo, expenseRepo, closes, sold, transferRepo, new InMemoryStockMovementRepository(),
            new InMemoryBranchRepository(), new NoopStockLock(), planner, calendar, tx);

    private final long classic;
    private final long green;
    private final long noVersion;
    private final long super_;
    private final long greens;

    {
        super_ = supplierRepo.save(new Supplier(null, "Súper", null, 1, null)).id();
        greens = supplierRepo.save(new Supplier(null, "Vegetales", null, 2, null)).id();
        supply(1, "Ravioles", Unit.G, "2000", super_, null, "10700");
        supply(2, "Leche", Unit.ML, "1000", super_, null, "1550");
        supply(3, "Zapallo", Unit.G, "1000", greens, null, "1800");
        supply(4, "Aceite", Unit.ML, "1500", super_, null, "4800");
        supply(5, "Bandeja", Unit.U, "1", super_, PackagingType.TRAY, "750");
        long ravioles = dishRepo.save(new Dish(null, "Ravioles", List.of(line(1, "320", Unit.G), line(2, "60", Unit.ML)),
                null)).id();
        long zapallo = dishRepo.save(new Dish(null, "Zapallo", List.of(line(3, "190", Unit.G)), null)).id();
        classic = packRepo.save(new Pack(null, 1, "Clásico", true, null, null)).id();
        green = packRepo.save(new Pack(null, 2, "Verde", true, null, null)).id();
        noVersion = packRepo.save(new Pack(null, 3, "Sin versión", true, null, null)).id();
        version(classic, ravioles, zapallo, ravioles, zapallo, ravioles);
        version(green, zapallo, zapallo, zapallo, zapallo, zapallo);
        settingsRepo.save(new Settings(new BigDecimal("45000"), new BigDecimal("20"), 4L, 1, 1,
                new BigDecimal("1000"), 3, 30, BigDecimal.TEN, List.of(), List.of(Settings.FIXED_EXPENSE_TYPE), null));
    }

    private void supply(long id, String name, Unit unit, String content, long supplierId, PackagingType pt,
                        String price) {
        supplyRepo.save(new Supply(id, name, unit, supplierId, null, new BigDecimal(content), null, pt, null, true,
                null));
        supplyRepo.addPrice(new SupplyPrice(null, id, LocalDate.of(2026, 11, 1), new BigDecimal(price)));
    }

    private static RecipeLine line(long supplyId, String quantity, Unit unit) {
        return new RecipeLine(supplyId, new BigDecimal(quantity), unit, BigDecimal.ONE);
    }

    private void version(long packId, long... dishes) {
        packRepo.addVersion(new PackVersion(null, packId, LocalDate.of(2026, 10, 1), List.of(new MenuSlot(1, dishes[0]),
                new MenuSlot(2, dishes[1]), new MenuSlot(3, dishes[2]), new MenuSlot(4, dishes[3]),
                new MenuSlot(5, dishes[4]))));
    }

    private ProductionSummary planClassic(int planned) {
        return service.plan(List.of(new PlanLine(classic, planned)), null, ACTOR);
    }

    private Production existing(int number, String start, ProductionStatus status) {
        LocalDate d = LocalDate.parse(start);
        return productionRepo.save(new Production(null, ProductionRules.lotCode(number, d), number, status, d,
                status == ProductionStatus.CLOSED ? d : null, List.of(new ProductionLine(classic, 5, null)),
                ProductionSnapshot.empty(settingsRepo.find().orElseThrow()), null, null, null));
    }

    private static void assertLines(Runnable call, String message) {
        assertThatThrownBy(call::run).isInstanceOf(ValidationException.class).hasMessage(message)
                .extracting("field").isEqualTo("lines");
    }

    @Test
    void planCreatesCodeSnapshotAndOnePendingPurchasePerSupplier() {
        existing(1, "2026-11-02", ProductionStatus.CLOSED);
        existing(2, "2026-11-05", ProductionStatus.PLANNED);
        existing(7, "2026-10-20", ProductionStatus.CLOSED);

        ProductionSummary s = service.plan(List.of(new PlanLine(classic, 10), new PlanLine(green, 0)), null, ACTOR);

        assertThat(s.code()).isEqualTo("3 NOV 26");
        assertThat(s.number()).isEqualTo(3);
        assertThat(s.start()).isEqualTo(TODAY);
        assertThat(s.status()).isEqualTo(ProductionStatus.PLANNED);
        assertThat(s.packs()).isEqualTo(10);
        assertThat(s.packLines()).singleElement().satisfies(l -> {
            assertThat(l.packId()).isEqualTo(classic);
            assertThat(l.name()).isEqualTo("Clásico");
            assertThat(l.versionId()).isNotNull();
        });
        Production saved = productionRepo.findById(s.id()).orElseThrow();
        assertThat(saved.snapshot().packs()).hasSize(1);
        assertThat(saved.snapshot().costs().get(1L)).isEqualByComparingTo("5.35");

        List<Purchase> ps = purchaseRepo.findByProduction(s.id());
        assertThat(ps).extracting(Purchase::supplierId).containsExactlyInAnyOrder(super_, greens);
        assertThat(ps).allSatisfy(p -> assertThat(p.status()).isEqualTo(PurchaseStatus.PENDING));
        Purchase sup = ps.stream().filter(p -> p.supplierId() == super_).findFirst().orElseThrow();
        assertThat(sup.lines()).extracting(PurchaseLine::supplyId).containsExactlyInAnyOrder(2L, 1L);
        PurchaseLine ravioles = sup.lines().stream().filter(l -> l.supplyId() == 1).findFirst().orElseThrow();
        assertThat(ravioles.quantity()).isEqualByComparingTo("9600");
        assertThat(ravioles.packagePrice()).isEqualByComparingTo("10700");

        assertThat(service.recipeBook(s.id())).singleElement().satisfies(r -> {
            assertThat(r.name()).isEqualTo("Clásico");
            assertThat(r.quantity()).isEqualTo(10);
            assertThat(r.menus()).hasSize(5);
        });
    }

    @Test
    void planValidationsInMockOrder() {
        assertLines(() -> service.plan(List.of(), null, ACTOR), "Elegí al menos un pack");
        assertLines(() -> service.plan(null, null, ACTOR), "Elegí al menos un pack");
        assertThatThrownBy(() -> service.plan(List.of(new PlanLine(99, 1)), null, ACTOR))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Pack no encontrado");
        assertLines(() -> service.plan(List.of(new PlanLine(classic, -1), new PlanLine(classic, 2)), null, ACTOR),
                "Las cantidades deben ser enteros ≥ 0");
        assertLines(() -> service.plan(List.of(new PlanLine(classic, 5), new PlanLine(classic, 3)), null, ACTOR),
                "Cada pack va una sola vez en la producción");
        assertLines(() -> service.plan(List.of(new PlanLine(classic, 0), new PlanLine(green, 0)), null, ACTOR),
                "Indicá cuántos packs vas a producir");
        assertThat(productionRepo.findAll()).isEmpty();
    }

    @Test
    void recipeBookNamesTheSharingPack() {
        ProductionSummary s = service.plan(List.of(new PlanLine(classic, 10), new PlanLine(green, 4)), null, ACTOR);

        RecipeBookPack book = service.recipeBook(s.id()).stream().filter(r -> r.packId() == classic)
                .findFirst().orElseThrow();

        RecipeBookMenu ravioles = book.menus().get(0);
        assertThat(ravioles.dish()).isEqualTo("Ravioles");
        assertThat(ravioles.sharedWith()).isNull();
        assertThat(ravioles.combinedTotal()).isNull();
        RecipeBookMenu zapallo = book.menus().get(1);
        assertThat(zapallo.sharedWith()).isEqualTo(new RecipeBookMenu.SharedWith(green, "Verde", 4));
        assertThat(zapallo.combinedTotal()).isEqualTo(14);
        assertThat(zapallo.ingredients()).singleElement()
                .satisfies(i -> assertThat(i.quantity()).isEqualByComparingTo("1900"));
    }

    @Test
    void regenerateRoundsToColumnScalesSoUnchangedListsStayEqual() {
        ProductionSummary s = planClassic(10);
        Map<Long, BigDecimal> leftovers = Map.of(2L, new BigDecimal("0.0004")); // leche 1799.9996 → 1800.000

        service.setLeftovers(s.id(), leftovers, null, ACTOR);
        Purchase first = purchaseRepo.findByProduction(s.id()).stream().filter(p -> p.supplierId() == super_)
                .findFirst().orElseThrow();
        service.setLeftovers(s.id(), leftovers, null, ACTOR);
        Purchase second = purchaseRepo.findById(first.id()).orElseThrow();

        assertThat(first.lines()).allSatisfy(l -> {
            assertThat(l.quantity().scale()).isEqualTo(3);
            assertThat(l.packagePrice().scale()).isEqualTo(2);
        });
        assertThat(first.lines()).anySatisfy(l -> assertThat(l.quantity()).isEqualTo(new BigDecimal("1800.000")));
        assertThat(second.lines()).isEqualTo(first.lines()); // record equals: scale-sensitive, like the adapter's check
    }

    @Test
    void regenerateDropsDuplicatePendings() {
        ProductionSummary s = planClassic(10);
        Purchase p = purchaseRepo.findByProduction(s.id()).get(0);
        purchaseRepo.save(new Purchase(null, s.id(), p.supplierId(), PurchaseStatus.PENDING, null, p.lines(), null,
                null));

        service.setLeftovers(s.id(), Map.of(), null, ACTOR);

        assertThat(purchaseRepo.findByProduction(s.id())).extracting(Purchase::supplierId)
                .containsExactlyInAnyOrder(super_, greens);
        assertThat(purchaseRepo.findById(p.id())).isPresent();
    }

    @Test
    void packWithoutCurrentVersionIs400() {
        assertThatThrownBy(() -> service.plan(List.of(new PlanLine(noVersion, 4)), null, ACTOR))
                .isInstanceOf(ValidationException.class).extracting("field").isEqualTo("lines");
        assertThat(productionRepo.findAll()).isEmpty();
        assertThat(purchaseRepo.findAll()).isEmpty();
    }

    @Test
    void summaryUsesReferenceOverheadAndPayback() {
        ProductionSummary planned = planClassic(10);
        sold.sold.put(planned.id(), 4);

        ProductionSummary s = service.get(planned.id()).summary();

        assertThat(s.overhead().value()).isEqualByComparingTo("1000");
        assertThat(s.overhead().reference()).isTrue();
        assertThat(s.overhead().provisional()).isTrue();
        ProductionSummary.PackLine l = s.packLines().get(0);
        assertThat(l.directEstimated()).isPositive();
        assertThat(l.costPerPack()).isEqualByComparingTo(l.directActual().add(new BigDecimal("1000")));
        assertThat(s.estimatedCost())
                .isEqualByComparingTo(l.directEstimated().add(new BigDecimal("1000")).multiply(BigDecimal.TEN));
        assertThat(s.actualCost()).isEqualByComparingTo(s.estimatedCost());
        assertThat(s.sold()).isEqualTo(4);
        assertThat(s.soldRatio()).isEqualByComparingTo("0.4");
        int needed = s.actualCost().divide(new BigDecimal("45000"), 0, RoundingMode.CEILING).intValueExact();
        assertThat(s.payback().needed()).isEqualTo(needed);
        assertThat(s.payback().missing()).isEqualTo(Math.max(0, needed - 4));
        assertThat(service.list(new ListQuery(0, 20, null, null)).items())
                .extracting(ProductionSummary::id).containsExactly(planned.id());
    }

    @Test
    void listSortsByStartDescByDefaultWithIdTieBreak() {
        Production a = existing(1, "2026-11-02", ProductionStatus.CLOSED);
        Production b = existing(2, "2026-11-05", ProductionStatus.PLANNED);
        Production c = existing(3, "2026-11-05", ProductionStatus.PLANNED);
        assertThat(service.list(new ListQuery(0, 20, null, null)).items()).extracting(ProductionSummary::id)
                .containsExactly(b.id(), c.id(), a.id()); // tie-break by id stays ascending (Listing)
        assertThat(service.list(new ListQuery(0, 20, "code", "asc")).items()).extracting(ProductionSummary::id)
                .containsExactly(a.id(), b.id(), c.id());
        assertThatThrownBy(() -> service.list(new ListQuery(0, 20, "packs", null)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void leftoversRegeneratePendingKeepingIdsAndDropEmptyLines() {
        ProductionSummary s = planClassic(10);
        long superId = purchaseRepo.findByProduction(s.id()).stream().filter(p -> p.supplierId() == super_)
                .findFirst().orElseThrow().id();

        service.setLeftovers(s.id(), Map.of(2L, new BigDecimal("100000"), 3L, new BigDecimal("5000")), null, ACTOR);

        List<Purchase> ps = purchaseRepo.findByProduction(s.id());
        assertThat(ps).singleElement().satisfies(p -> {
            assertThat(p.id()).isEqualTo(superId);
            assertThat(p.lines()).extracting(PurchaseLine::supplyId).containsExactly(1L);
        });
        assertThat(service.get(s.id()).leftovers()).containsEntry(3L, new BigDecimal("5000"));
        assertThat(service.shoppingList(s.id())).extracting(ShoppingListGroup::supplierId).containsExactly(super_);
    }

    @Test
    void leftoversValidation() {
        ProductionSummary s = planClassic(10);
        Map<Long, BigDecimal> withNull = new HashMap<>();
        withNull.put(1L, null);
        assertThatThrownBy(() -> service.setLeftovers(s.id(), withNull, null, ACTOR))
                .isInstanceOf(ValidationException.class).hasMessage("Los sobrantes deben ser ≥ 0")
                .extracting("field").isEqualTo("leftovers");
        assertThatThrownBy(() -> service.setLeftovers(s.id(), Map.of(1L, new BigDecimal("-1")), null, ACTOR))
                .isInstanceOf(ValidationException.class).hasMessage("Los sobrantes deben ser ≥ 0");
        assertThatThrownBy(() -> service.setLeftovers(s.id(), Map.of(99L, BigDecimal.ONE), null, ACTOR))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Insumo no encontrado");
        assertThatThrownBy(() -> service.setLeftovers(999, Map.of(), null, ACTOR))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Producción no encontrada");
    }

    @Test
    void leftoversOnClosedProductionIs409() {
        Production closed = existing(1, "2026-11-02", ProductionStatus.CLOSED);
        assertThatThrownBy(() -> service.setLeftovers(closed.id(), Map.of(), null, ACTOR))
                .isInstanceOf(ConflictException.class).hasMessage("La producción ya está cerrada")
                .extracting("field").isEqualTo("status");
    }

    @Test
    void annulRemovesPendingPurchasesAndRejectsWhenBought() {
        ProductionSummary a = planClassic(10);
        service.annul(a.id(), ACTOR);
        assertThat(productionRepo.findById(a.id())).isEmpty();
        assertThat(purchaseRepo.findByProduction(a.id())).isEmpty();

        ProductionSummary b = planClassic(10);
        Purchase p = purchaseRepo.findByProduction(b.id()).get(0);
        purchaseRepo.save(new Purchase(p.id(), p.productionId(), p.supplierId(), PurchaseStatus.BOUGHT, TODAY,
                p.lines(), new BigDecimal("100"), p.version()));
        assertThatThrownBy(() -> service.annul(b.id(), ACTOR))
                .isInstanceOf(ConflictException.class).hasMessage("Tiene compras registradas; anulalas primero")
                .extracting("field").isNull();
        assertThat(purchaseRepo.findByProduction(b.id())).hasSize(2);

        Production closed = existing(1, "2026-11-02", ProductionStatus.CLOSED);
        assertThatThrownBy(() -> service.annul(closed.id(), ACTOR))
                .isInstanceOf(ConflictException.class).hasMessage("La producción ya está cerrada");
    }

    @Test
    void annulledLotCodeIsNotReused() {
        ProductionSummary first = planClassic(10);
        service.annul(first.id(), ACTOR);
        assertThat(planClassic(10).code()).isNotEqualTo(first.code());
    }

    @Test
    void boughtSupplierIsNotRegenerated() {
        ProductionSummary s = planClassic(10);
        Purchase p = purchaseRepo.findByProduction(s.id()).stream().filter(x -> x.supplierId() == greens)
                .findFirst().orElseThrow();
        Purchase bought = purchaseRepo.save(new Purchase(p.id(), p.productionId(), p.supplierId(),
                PurchaseStatus.BOUGHT, TODAY, p.lines(), new BigDecimal("100"), p.version()));

        service.setLeftovers(s.id(), Map.of(3L, new BigDecimal("5000")), null, ACTOR);

        assertThat(purchaseRepo.findById(p.id())).contains(bought);
        assertThat(service.shoppingList(s.id())).extracting(ShoppingListGroup::supplierId).containsExactly(super_);
    }

    @Test
    void shoppingListTotalInPacksOneDecimal() {
        ProductionSummary s = planClassic(10);
        long superId = purchaseRepo.findByProduction(s.id()).stream().filter(p -> p.supplierId() == super_)
                .findFirst().orElseThrow().id();

        ShoppingListGroup g = service.shoppingList(s.id()).get(0);

        assertThat(g.supplier()).isEqualTo("Súper");
        // ravioles 10 × 3 × 320 g × 5,35 + leche 10 × 3 × 60 ml × 1,55 = 51360 + 2790
        assertThat(g.total()).isEqualByComparingTo("54150");
        assertThat(g.totalInPacks()).isEqualTo(new BigDecimal("1.2"));
        assertThat(g.purchaseId()).isEqualTo(superId);
        assertThat(g.status()).isEqualTo(PurchaseStatus.PENDING);
    }
}
