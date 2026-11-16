package com.fooda.api.core.domain.production;

import com.fooda.api.core.domain.catalog.Composite;
import com.fooda.api.core.domain.catalog.CompositeRule;
import com.fooda.api.core.domain.catalog.Dish;
import com.fooda.api.core.domain.catalog.MenuSlot;
import com.fooda.api.core.domain.catalog.PackVersion;
import com.fooda.api.core.domain.catalog.PackagingType;
import com.fooda.api.core.domain.catalog.RecipeLine;
import com.fooda.api.core.domain.catalog.Settings;
import com.fooda.api.core.domain.catalog.Supplier;
import com.fooda.api.core.domain.catalog.Supply;
import com.fooda.api.core.domain.catalog.SupplyPrice;
import com.fooda.api.core.domain.catalog.Unit;
import com.fooda.api.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Port de fooda-admin/src/lib/domain/produccion.test.ts (+ montoCompra). Fixtures nuevos por test (JUnit). */
class ProductionRulesTest {

    private static final LocalDate START = LocalDate.parse("2026-11-03");

    private final List<Supplier> suppliers = List.of(
            new Supplier(1L, "Súper", null, 1, null), new Supplier(2L, "Vegetales", null, 2, null),
            new Supplier(3L, "Belén", null, 3, null));
    private final List<Supply> supplies = new ArrayList<>(List.of(
            sup(1, "Ravioles", Unit.G, "2000", 1, null, null), sup(2, "Leche", Unit.ML, "1000", 1, null, null),
            sup(3, "Zapallo", Unit.G, "1000", 2, null, null), sup(4, "Calabaza", Unit.G, "1000", 2, null, null),
            sup(5, "Pimiento rojo", Unit.G, "1000", 2, null, null),
            sup(6, "Pimiento verde", Unit.G, "1000", 2, null, null),
            sup(7, "Aceite", Unit.ML, "1500", 1, null, null),
            sup(8, "Bandeja", Unit.U, "1", 1, PackagingType.TRAY, null),
            sup(9, "Sticker", Unit.U, "1", 3, PackagingType.STICKER, null),
            sup(10, "Calabaza/zapallo", Unit.G, "1", 2, null, new Composite(CompositeRule.MAX, List.of(3L, 4L))),
            sup(11, "Pimiento (50/50)", Unit.G, "1", 2, null, new Composite(CompositeRule.AVERAGE, List.of(5L, 6L)))));
    private final List<SupplyPrice> prices = new ArrayList<>(List.of(price(1, "10700"), price(2, "1550"),
            price(3, "1800"), price(4, "2500"), price(5, "3000"), price(6, "2000"), price(7, "4800"),
            price(8, "750"), price(9, "1500")));
    private final List<Dish> dishes = new ArrayList<>(List.of(
            new Dish(1L, "Ravioles con Queso Azul", List.of(l(1, "320"), l(2, "60", Unit.ML)), null),
            new Dish(2L, "Canelones de Calabaza", List.of(l(10, "190"), l(2, "300", Unit.ML)), null),
            new Dish(3L, "Ravioles con Morrones", List.of(l(1, "320"), l(11, "150")), null)));
    // El front usa 2 menús; PackVersion exige 5, así que se rellena con platos inexistentes (no suman nada).
    private final List<PackVersion> versions = new ArrayList<>(List.of(
            version(1, 1, "2026-10-01", 1, 1, 4, 2),
            version(5, 5, "2026-10-01", 21, 2, 23, 3)));
    private final Settings settings = new Settings(new BigDecimal("45000"), new BigDecimal("20"), 7L, 5, 1,
            new BigDecimal("1000"), 2, 30, new BigDecimal("0.15"), List.of(), List.of(Settings.FIXED_EXPENSE_TYPE),
            null);

    private static Supply sup(long id, String name, Unit unit, String content, long supplierId, PackagingType pt,
                              Composite composite) {
        return new Supply(id, name, unit, supplierId, null, new BigDecimal(content), null, pt, composite, true, null);
    }

    private static SupplyPrice price(long supplyId, String packagePrice) {
        return price(supplyId, packagePrice, "2026-11-01");
    }

    private static SupplyPrice price(long supplyId, String packagePrice, String date) {
        return new SupplyPrice(supplyId * 100, supplyId, LocalDate.parse(date), new BigDecimal(packagePrice));
    }

    private static RecipeLine l(long supplyId, String quantity) {
        return l(supplyId, quantity, Unit.G);
    }

    private static RecipeLine l(long supplyId, String quantity, Unit unit) {
        return new RecipeLine(supplyId, new BigDecimal(quantity), unit, BigDecimal.ONE);
    }

    private static PackVersion version(long id, long packId, String from, int n1, long d1, int n2, long d2) {
        long pad = packId * 100;
        return new PackVersion(id, packId, LocalDate.parse(from), List.of(new MenuSlot(n1, d1), new MenuSlot(n2, d2),
                new MenuSlot(n2 + 1, pad + 1), new MenuSlot(n2 + 2, pad + 2), new MenuSlot(n2 + 3, pad + 3)));
    }

    private Production production(List<ProductionLine> lines, Map<Long, BigDecimal> paid, Map<Long, BigDecimal> left) {
        ProductionSnapshot s = ProductionRules.takeSnapshot(lines.stream().map(ProductionLine::packId).toList(),
                versions, dishes, supplies, prices, settings, START);
        return new Production(1L, "1 NOV 26", 1, ProductionStatus.PLANNED, START, null, lines, s, paid, left, null);
    }

    private Production production(ProductionLine... lines) {
        return production(List.of(lines), Map.of(), Map.of());
    }

    private static ProductionLine line(long packId, int planned) {
        return new ProductionLine(packId, planned, null);
    }

    private static void assertNear(BigDecimal actual, double expected) {
        assertThat(actual.subtract(BigDecimal.valueOf(expected)).abs())
                .as("%s ≉ %s", actual, expected)
                .isLessThan(new BigDecimal("0.01"));
    }

    private static ShoppingItem item(List<ShoppingGroup> list, String supplier, String name) {
        return list.stream().filter(g -> g.supplier().name().equals(supplier)).findFirst().orElseThrow()
                .items().stream().filter(i -> i.name().equals(name)).findFirst().orElse(null);
    }

    @Test
    void codeOrderIsChronological() {
        assertThat(new ArrayList<>(List.of("1 DIC 26", "10 NOV 26", "lote inicial", "2 NOV 26", "1 ENE 27")).stream()
                .sorted(ProductionRules.CODE_ORDER).toList())
                .containsExactly("2 NOV 26", "10 NOV 26", "1 DIC 26", "1 ENE 27", "lote inicial");
    }

    @Test
    void shoppingListOfLotWithoutSnapshotIsEmpty() {
        Production p = new Production(1L, "0 NOV 26", 0, ProductionStatus.PLANNED, LocalDate.of(2026, 11, 1), null,
                List.of(), null, null, null, null);
        assertThat(ProductionRules.shoppingList(p, List.of(), List.of())).isEmpty();
    }

    @Test
    void lotCode() {
        assertThat(ProductionRules.lotCode(1, LocalDate.parse("2026-11-03"))).isEqualTo("1 NOV 26");
        assertThat(ProductionRules.lotCode(12, LocalDate.parse("2027-01-30"))).isEqualTo("12 ENE 27");
    }

    @Test
    void snapshotFreezesPricesRecipesAndVersion() {
        Production p = production(line(1, 10));
        BigDecimal before = ProductionRules.packCost(p, 1, supplies).estimated();
        prices.add(price(1, "99999", "2026-11-05"));
        dishes.set(0, new Dish(1L, "Ravioles con Queso Azul",
                List.of(l(1, "320"), l(2, "60", Unit.ML), l(2, "1000", Unit.ML)), null));
        versions.add(version(2, 1, "2026-11-04", 1, 3, 4, 2));
        assertThat(ProductionRules.packCost(p, 1, supplies).estimated()).isEqualByComparingTo(before);
    }

    @Test
    void snapshotUsesVersionWithLatestValidFromUpToDate() {
        versions.add(version(2, 1, "2026-11-04", 1, 3, 4, 2));
        ProductionSnapshot s = ProductionRules.takeSnapshot(List.of(1L), versions, dishes, supplies, prices, settings,
                LocalDate.parse("2026-11-10"));
        assertThat(s.packs().get(0).versionId()).isEqualTo(2);
        assertThat(s.dishes()).containsKey(3L).doesNotContainKey(1L);
    }

    @Test
    void snapshotWithoutCurrentVersionFails() {
        assertThatThrownBy(() -> ProductionRules.takeSnapshot(List.of(1L), versions, dishes, supplies, prices,
                settings, LocalDate.parse("2026-09-30")))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.field()).isEqualTo("lines");
                    assertThat(e.getMessage()).isEqualTo("El pack 1 no tiene versión vigente al 2026-09-30");
                });
    }

    @Test
    void estimatedCostIsRecipesOilAndPackaging() {
        // ravioles 1712 + 93 + canelones 475 + 465 = 2745; aceite 64; packaging 5250
        assertNear(ProductionRules.packCost(production(line(1, 10)), 1, supplies).estimated(), 2745 + 64 + 5250);
    }

    @Test
    void actualCostUsesPaidPrices() {
        Production p = production(List.of(line(1, 10)), Map.of(1L, new BigDecimal("6")), Map.of()); // 5,35 → 6
        PackCostInProduction c = ProductionRules.packCost(p, 1, supplies);
        assertNear(c.actual().subtract(c.estimated()), 320 * (6 - 5.35));
    }

    @Test
    void packNotInSnapshotCostsZero() {
        PackCostInProduction c = ProductionRules.packCost(production(line(1, 10)), 5, supplies);
        assertThat(c.estimated()).isEqualByComparingTo("0");
        assertThat(c.actual()).isEqualByComparingTo("0");
    }

    @Test
    void shoppingListBySupplierWithoutPackaging() {
        List<ShoppingGroup> list = ProductionRules.shoppingList(production(line(1, 10), line(5, 10)), supplies,
                suppliers);
        assertThat(list).extracting(g -> g.supplier().name()).containsExactly("Súper", "Vegetales");
        ShoppingItem rav = item(list, "Súper", "Ravioles");
        assertThat(rav.quantity()).isEqualByComparingTo("6400");
        assertNear(rav.total(), 6400 * 5.35);
        assertThat(list).flatExtracting(ShoppingGroup::items).extracting(ShoppingItem::name)
                .doesNotContain("Bandeja", "Sticker");
    }

    @Test
    void shoppingListSplitsAverageAndSendsMaxToMostExpensive() {
        List<ShoppingGroup> list = ProductionRules.shoppingList(production(line(1, 10), line(5, 10)), supplies,
                suppliers);
        assertThat(item(list, "Vegetales", "Calabaza").quantity()).isEqualByComparingTo("3800"); // 190 × 20
        assertThat(item(list, "Vegetales", "Zapallo")).isNull();
        assertThat(item(list, "Vegetales", "Pimiento rojo").quantity()).isEqualByComparingTo("750"); // 150 × 10 / 2
        assertThat(item(list, "Vegetales", "Pimiento verde").quantity()).isEqualByComparingTo("750");
    }

    @Test
    void shoppingListSubtractsLeftoversWithoutGoingBelowZero() {
        Production p = production(List.of(line(1, 10)), Map.of(),
                Map.of(1L, new BigDecimal("1000"), 2L, new BigDecimal("99999")));
        List<ShoppingGroup> list = ProductionRules.shoppingList(p, supplies, suppliers);
        assertThat(item(list, "Súper", "Ravioles").quantity()).isEqualByComparingTo("2200");
        assertThat(item(list, "Súper", "Leche")).isNull();
    }

    @Test
    void shoppingListGroupsByLiveSupplierAndSortsItemsByName() {
        Production p = production(line(1, 10));
        List<Supply> live = new ArrayList<>(supplies);
        live.set(1, sup(2, "Leche", Unit.ML, "1000", 3, null, null)); // la leche ahora se compra en Belén
        List<ShoppingGroup> list = ProductionRules.shoppingList(p, live, suppliers);
        assertThat(list).extracting(g -> g.supplier().name()).containsExactly("Súper", "Vegetales", "Belén");
        assertThat(list.get(0).items()).extracting(ShoppingItem::name).containsExactly("Ravioles");
        assertThat(list.get(1).items()).extracting(ShoppingItem::name).containsExactly("Calabaza");
        assertNear(list.get(0).total(), 3200 * 5.35);
    }

    @Test
    void recipeBookSharedDishCombinesTotals() {
        List<ScaledPack> r = ProductionRules.recipeBook(production(line(1, 10), line(5, 5)), supplies);
        ScaledPack pack1 = r.stream().filter(p -> p.packId() == 1).findFirst().orElseThrow();
        ScaledMenu can1 = pack1.menus().stream().filter(m -> m.number() == 4).findFirst().orElseThrow();
        assertThat(can1.ingredients().stream().filter(i -> i.name().equals("Calabaza/zapallo")).findFirst()
                .orElseThrow().quantity()).isEqualByComparingTo("1900");
        assertThat(can1.sharedWith()).isEqualTo(new ScaledMenu.SharedWith(5, 5));
        assertThat(can1.combinedTotal()).isEqualTo(15);
        ScaledMenu rav = pack1.menus().stream().filter(m -> m.number() == 1).findFirst().orElseThrow();
        assertThat(rav.sharedWith()).isNull();
        assertThat(rav.combinedTotal()).isNull();
    }

    @Test
    void currentVersionTieOnDateTakesHighestId() {
        versions.add(version(7, 1, "2026-10-01", 1, 3, 4, 2));
        ProductionSnapshot s = ProductionRules.takeSnapshot(List.of(1L), versions, dishes, supplies, prices, settings,
                START);
        assertThat(s.packs().get(0).versionId()).isEqualTo(7);
    }

    @Test
    void purchaseAmountRoundsHalfUp() {
        // ravioles 3000 g a 10700 por 2000 g = 16050; 1001 g → 5355.35 → 5355
        assertThat(ProductionRules.purchaseAmount(supplies,
                List.of(new PurchaseLine(1, new BigDecimal("3000"), new BigDecimal("10700")))))
                .isEqualByComparingTo("16050");
        assertThat(ProductionRules.purchaseAmount(supplies,
                List.of(new PurchaseLine(1, new BigDecimal("1001"), new BigDecimal("10700")))))
                .isEqualByComparingTo("5355");
        // insumo desconocido → contenido 1: 0.5 × 1 = 0.5 → 1
        assertThat(ProductionRules.purchaseAmount(supplies,
                List.of(new PurchaseLine(99, new BigDecimal("0.5"), BigDecimal.ONE))))
                .isEqualByComparingTo("1");
    }
}
