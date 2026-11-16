package com.fooda.api.catalog.application.service;

import com.fooda.api.production.fakes.NoopStockLock;
import com.fooda.api.core.domain.production.Production;
import com.fooda.api.core.domain.production.ProductionLine;
import com.fooda.api.core.domain.production.ProductionStatus;
import com.fooda.api.core.domain.stock.MovementType;
import com.fooda.api.core.domain.stock.StockMovement;
import com.fooda.api.production.fakes.InMemoryProductionRepository;
import com.fooda.api.production.fakes.InMemoryStockMovementRepository;
import com.fooda.api.catalog.application.port.in.*;
import com.fooda.api.catalog.fakes.*;
import com.fooda.api.core.domain.catalog.*;
import com.fooda.api.shared.DirectTransactions;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.shared.application.Field;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.exception.ResourceNotFoundException;
import com.fooda.api.shared.domain.ConflictException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DishPackServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);
    private static final LocalDate D = LocalDate.of(2026, 11, 1);

    private final InMemorySupplyRepository supplyRepo = new InMemorySupplyRepository();
    private final InMemoryDishRepository dishRepo = new InMemoryDishRepository();
    private final InMemoryPackRepository packRepo = new InMemoryPackRepository();
    private final InMemorySettingsRepository settingsRepo = new InMemorySettingsRepository();
    private final BusinessCalendar calendar =
            new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), ZoneOffset.UTC));
    private final DishUseCase dishes = new DishService(dishRepo, packRepo, supplyRepo, calendar,
            new DirectTransactions());
    private final InMemoryProductionRepository productionRepo = new InMemoryProductionRepository();
    private final InMemoryStockMovementRepository movementRepo = new InMemoryStockMovementRepository();
    private final PackUseCase packs = new PackService(packRepo, dishRepo, supplyRepo, settingsRepo, productionRepo,
            movementRepo, new NoopStockLock(), calendar, new DirectTransactions());

    {
        // Subset of the CostsTest fixture (port of costos.test.ts) used by ravioles and canelones.
        row(1, Unit.G, "10700", "2000");
        row(2, Unit.G, "7000", "1000");
        row(3, Unit.G, "19200", "1000");
        row(4, Unit.ML, "1550", "1000");
        row(5, Unit.G, "9900", "1000");
        row(6, Unit.G, "4500", "1000");
        row(7, Unit.G, "1400", "100");
        row(8, Unit.U, "5000", "30");
        row(9, Unit.G, "0", "1000");
        row(10, Unit.G, "9900", "1000");
        row(11, Unit.G, "1800", "1000");
        row(12, Unit.G, "2500", "1000");
        row(13, Unit.G, "7000", "1000");
        row(14, Unit.G, "7400", "1000");
        row(15, Unit.G, "12000", "10000");
        row(19, Unit.ML, "4800", "1500");
        supplyRepo.save(new Supply(31L, "Calabaza/zapallo", Unit.G, 1, null, BigDecimal.ONE, null, null,
                new Composite(CompositeRule.MAX, List.of(11L, 12L)), true, null));
        packaging(40, PackagingType.TRAY, "750");
        packaging(41, PackagingType.STICKER, "1500");
        settingsRepo.save(new Settings(new BigDecimal("45000"), new BigDecimal("20"), 19L, 5, 1,
                new BigDecimal("1000"), 3, 30, BigDecimal.TEN, List.of(), List.of(Settings.FIXED_EXPENSE_TYPE), null));
    }

    private void row(long id, Unit unit, String price, String content) {
        supplyRepo.save(new Supply(id, "Insumo " + id, unit, 1, null, new BigDecimal(content), null, null, null,
                true, null));
        supplyRepo.addPrice(new SupplyPrice(null, id, D, new BigDecimal(price)));
    }

    private void packaging(long id, PackagingType type, String price) {
        supplyRepo.save(new Supply(id, type.name(), Unit.U, 1, null, BigDecimal.ONE, null, type, null, true, null));
        supplyRepo.addPrice(new SupplyPrice(null, id, D, new BigDecimal(price)));
    }

    private static RecipeLine l(long supplyId, String quantity) {
        return l(supplyId, quantity, Unit.G);
    }

    private static RecipeLine l(long supplyId, String quantity, Unit unit) {
        return new RecipeLine(supplyId, new BigDecimal(quantity), unit, BigDecimal.ONE);
    }

    private long dish(String name, RecipeLine... recipe) {
        return dishes.create(new Dish(null, name, List.of(recipe), null)).dish().id();
    }

    /** Zero-cost dish: a line of Harina (id 9, price 0). */
    private long freeDish(String name) {
        return dish(name, l(9, "1"));
    }

    private static List<MenuSlot> menus(long... dishIds) {
        return java.util.stream.IntStream.range(0, dishIds.length)
                .mapToObj(i -> new MenuSlot(i + 1, dishIds[i])).toList();
    }

    private static void assertNear(BigDecimal actual, double expected) {
        assertThat(actual.subtract(BigDecimal.valueOf(expected)).abs())
                .as("%s ≉ %s", actual, expected).isLessThan(new BigDecimal("0.01"));
    }

    @Test void packViewMatchesFrontNumbers() {
        long ravioles = dish("Ravioles", l(1, "320"), l(2, "30"), l(3, "25"), l(4, "60", Unit.ML), l(5, "35"),
                l(6, "10"), l(7, "20"));
        long canelones = dish("Canelones", l(4, "112.5", Unit.ML), l(8, "0.45", Unit.U), l(9, "56.25"),
                l(10, "1.125"), l(31, "190"), l(13, "70"), l(14, "50"), l(15, "40"), l(4, "300", Unit.ML),
                l(8, "1", Unit.U), l(10, "80"), l(6, "30"));
        long packId = packs.create("Clásico").pack().id();
        packs.addVersion(packId, D, menus(ravioles, canelones, freeDish("A"), freeDish("B"), freeDish("C")));

        PackView v = packs.get(packId);

        assertThat(v.salePrice()).isEqualByComparingTo("45000");
        assertNear(v.cost().recipes(), 6368.68);
        assertNear(v.cost().oil(), 64);
        assertThat(v.cost().packaging()).isEqualByComparingTo("5250");
        assertNear(v.cost().direct(), 11682.68);
        assertThat(v.cost().overhead()).isEqualByComparingTo("1000");
        assertNear(v.cost().total(), 12682.68);
        assertNear(v.profit(), 32317.32);
        assertThat(v.margin()).isEqualByComparingTo(v.profit().divide(new BigDecimal("45000"),
                com.fooda.api.shared.domain.Decimals.MC));
        assertThat(v.currentVersion().menus()).extracting(PackView.MenuView::dishName)
                .containsExactly("Ravioles", "Canelones", "A", "B", "C");
        assertNear(v.currentVersion().menus().get(0).cost(), 3166.5);
        assertNear(dishes.get(canelones).cost(), 3202.18);
        assertThat(packs.list()).singleElement().extracting(p -> p.cost().total()).isEqualTo(v.cost().total());
    }

    @Test void packWithoutVersionHasNullCostAndProfitEqualsPrice() {
        long id = packs.create("Nuevo").pack().id();
        packs.update(id, new PackChanges(Field.absent(), Field.absent(), Field.of(new BigDecimal("50000")), Field.absent()));
        PackView v = packs.get(id);
        assertThat(v.currentVersion()).isNull();
        assertThat(v.cost()).isNull();
        assertThat(v.salePrice()).isEqualByComparingTo("50000");
        assertThat(v.profit()).isEqualByComparingTo("50000");
        assertThat(v.margin()).isEqualByComparingTo("1");
    }

    @Test void newVersionDoesNotTouchPrevious() {
        long a = freeDish("A");
        long b = freeDish("B");
        long packId = packs.create("Pack").pack().id();
        PackVersion first = packs.addVersion(packId, D, menus(a, a, a, a, a));
        PackVersion second = packs.addVersion(packId, null, menus(b, b, b, b, b));
        assertThat(second.validFrom()).isEqualTo(TODAY);
        assertThat(packs.versions(packId)).containsExactly(first, second);
        assertThat(packs.get(packId).currentVersion().version()).isEqualTo(second);
        packs.addVersion(packId, TODAY.plusDays(1), menus(a, b, a, b, a));
        assertThat(packs.get(packId).currentVersion().version()).isEqualTo(second);
    }

    @Test void addVersionWithMissingDishIs404() {
        long a = freeDish("A");
        long packId = packs.create("Pack").pack().id();
        assertThatThrownBy(() -> packs.addVersion(packId, null, menus(a, a, 999, a, a)))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Plato no encontrado");
    }

    @Test void dishMenusComeFromCurrentVersions() {
        long a = freeDish("A");
        long x = freeDish("X");
        long packId = packs.create("Pack").pack().id();
        packs.addVersion(packId, D, menus(a, a, x, a, a));
        packs.addVersion(packId, TODAY.plusDays(1), menus(x, a, a, a, a));
        assertThat(dishes.get(x).menus()).containsExactly(new DishView.MenuRef(3, packId));
        assertThat(dishes.list(new ListQuery(0, 10, null, null)).items())
                .filteredOn(v -> v.dish().id() == x).singleElement()
                .extracting(DishView::menus).isEqualTo(List.of(new DishView.MenuRef(3, packId)));
    }

    @Test void dishUsedInPackCannotBeDeleted() {
        long a = freeDish("A");
        long unused = freeDish("B");
        packs.addVersion(packs.create("Pack").pack().id(), null, menus(a, a, a, a, a));
        assertThatThrownBy(() -> dishes.delete(a, 1L))
                .isInstanceOf(ConflictException.class).hasMessage("El plato está en uso en un pack");
        dishes.delete(unused, 1L);
        assertThatThrownBy(() -> dishes.get(unused))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Plato no encontrado");
    }

    @Test void packInAProductionOrStockCannotBeDeleted() {
        long planned = packs.create("Planificado").pack().id();
        long moved = packs.create("Movido").pack().id();
        productionRepo.save(new Production(null, "1 NOV 26", 1, ProductionStatus.PLANNED, TODAY, null,
                List.of(new ProductionLine(planned, 10, null)), null, null, null, null));
        movementRepo.add(new StockMovement(null, TODAY, 1L, 1L, moved, 5, MovementType.ADJUSTMENT, null, "x"));
        for (long id : new long[]{planned, moved}) {
            assertThatThrownBy(() -> packs.delete(id, 1L)).isInstanceOf(ConflictException.class)
                    .hasMessage("El pack está en uso; desactivalo en lugar de borrarlo");
        }
        long free = packs.create("Libre").pack().id();
        packs.delete(free, 1L);
        assertThat(packs.list()).extracting(v -> v.pack().id()).containsExactly(planned, moved);
    }

    @Test void packNumbersAreSequential() {
        assertThat(packs.create("Uno").pack().number()).isEqualTo(1);
        PackView two = packs.create("Dos");
        assertThat(two.pack().number()).isEqualTo(2);
        assertThat(two.pack().active()).isTrue();
        assertThat(two.pack().price()).isNull();
        assertThat(packs.list()).extracting(v -> v.pack().number()).containsExactly(1, 2);
    }

    @Test void editingRecipeReplacesIt() {
        long id = dish("Torta", l(1, "100"), l(2, "100"));
        var c = new DishChanges(Field.absent(), Field.of(List.of(l(7, "100"))), Field.absent());
        DishView v = dishes.update(id, c);
        assertThat(v.dish().recipe()).containsExactly(l(7, "100"));
        assertThat(v.dish().name()).isEqualTo("Torta");
        assertThat(v.cost()).isEqualByComparingTo("1400");
    }

    @Test void recipeWithMissingSupplyIs404() {
        assertThatThrownBy(() -> dish("X", l(999, "1")))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Insumo no encontrado");
    }

    @Test void dishListSortsByCost() {
        long cheap = freeDish("Barato");
        long pricey = dish("Caro", l(7, "100"));
        assertThat(dishes.list(new ListQuery(0, 10, "cost", "desc")).items())
                .extracting(v -> v.dish().id()).containsExactly(pricey, cheap);
    }

    @Test void packViewsNeedSettings() {
        long id = packs.create("Pack").pack().id();
        var empty = new PackService(packRepo, dishRepo, supplyRepo, new InMemorySettingsRepository(),
                productionRepo, movementRepo, new NoopStockLock(), calendar, new DirectTransactions());
        assertThatThrownBy(() -> empty.get(id))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Todavía no se cargaron los parámetros");
    }
}
