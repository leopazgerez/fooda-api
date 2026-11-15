package com.fooda.api.core.domain.catalog;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Port de fooda-admin/src/lib/domain/costos.test.ts. */
class CostsTest {

    private static final LocalDate D = LocalDate.parse("2026-11-01");

    private final Map<Long, Supply> supplies = new LinkedHashMap<>();
    private final List<SupplyPrice> prices = new ArrayList<>();
    private final Map<Long, BigDecimal> costs;

    // [id, nombre, unidad, precioEnvase, contenido, gramsPerUnit, composite]
    {
        row(1, "Ravioles", Unit.G, "10700", "2000");
        row(2, "Queso crema", Unit.G, "7000", "1000");
        row(3, "Queso azul", Unit.G, "19200", "1000");
        row(4, "Leche", Unit.ML, "1550", "1000");
        row(5, "Queso sardo", Unit.G, "9900", "1000");
        row(6, "Maicena", Unit.G, "4500", "1000");
        row(7, "Nueces", Unit.G, "1400", "100");
        row(8, "Huevo", Unit.U, "5000", "30");
        row(9, "Harina", Unit.G, "0", "1000");
        row(10, "Manteca", Unit.G, "9900", "1000");
        row(11, "Zapallo", Unit.G, "1800", "1000");
        row(12, "Calabaza", Unit.G, "2500", "1000");
        row(13, "Quinoa", Unit.G, "7000", "1000");
        row(14, "Muzzarella", Unit.G, "7400", "1000");
        row(15, "Cebolla", Unit.G, "12000", "10000");
        row(16, "Pimiento rojo", Unit.G, "3000", "1000");
        row(17, "Pimiento verde", Unit.G, "2000", "1000");
        add(new Supply(18L, "Repollo morado", Unit.G, 1, null, new BigDecimal("1000"), new BigDecimal("1000"),
                null, null, true, null), "2500");
        row(19, "Aceite", Unit.ML, "4800", "1500");
        row(20, "Salsa de tomate", Unit.G, "12100", "2900");
        row(21, "Pollo", Unit.G, "9500", "1000");
        add(composite(30, "Pimiento (50% rojo + 50% verde)", CompositeRule.AVERAGE, 16, 17), null);
        add(composite(31, "Calabaza/zapallo", CompositeRule.MAX, 11, 12), null);
        costs = Costs.currentCosts(supplies.values(), prices, null);
    }

    private final Dish ravioles = new Dish(1L, "Ravioles con Queso Azul y Nueces", List.of(
            l(1, "320"), l(2, "30"), l(3, "25"), l(4, "60", Unit.ML), l(5, "35"), l(6, "10"), l(7, "20")), null);
    private final Dish canelones = new Dish(4L, "Canelones de Crema de Calabaza", List.of(
            l(4, "112.5", Unit.ML), l(8, "0.45", Unit.U), l(9, "56.25"), l(10, "1.125"), l(31, "190"), l(13, "70"),
            l(14, "50"), l(15, "40"), l(4, "300", Unit.ML), l(8, "1", Unit.U), l(10, "80"), l(6, "30")), null);

    private void row(long id, String name, Unit unit, String price, String content) {
        add(new Supply(id, name, unit, 1, null, new BigDecimal(content), null, null, null, true, null), price);
    }

    private static Supply composite(long id, String name, CompositeRule rule, long... parts) {
        List<Long> ids = new ArrayList<>();
        for (long p : parts) {
            ids.add(p);
        }
        return new Supply(id, name, Unit.G, 1, null, BigDecimal.ONE, null, null, new Composite(rule, ids), true, null);
    }

    private void add(Supply s, String price) {
        supplies.put(s.id(), s);
        if (price != null) {
            prices.add(new SupplyPrice(s.id(), s.id(), D, new BigDecimal(price)));
        }
    }

    private static RecipeLine l(long supplyId, String quantity) {
        return l(supplyId, quantity, Unit.G);
    }

    private static RecipeLine l(long supplyId, String quantity, Unit unit) {
        return new RecipeLine(supplyId, new BigDecimal(quantity), unit, BigDecimal.ONE);
    }

    private static void assertNear(BigDecimal actual, double expected, double tol) {
        assertThat(actual.subtract(BigDecimal.valueOf(expected)).abs())
                .as("%s ≉ %s", actual, expected)
                .isLessThan(BigDecimal.valueOf(tol));
    }

    private static void assertNear(BigDecimal actual, double expected) {
        assertNear(actual, expected, 0.01);
    }

    private static Settings settingsWithOil(Long oilSupplyId) {
        return new Settings(new BigDecimal("20000"), new BigDecimal("20"), oilSupplyId, 5, 1, BigDecimal.ZERO,
                3, 30, BigDecimal.TEN, List.of(), List.of(Settings.FIXED_EXPENSE_TYPE), null);
    }

    @Test
    void unitCostIsPackagePriceOverContent() {
        assertNear(Costs.unitCost(supplies.get(20L), prices, supplies, null), 4.1724, 0.0001);
    }

    @Test
    void unitCostUsesLatestPriceUntilDate() {
        List<SupplyPrice> ps = List.of(
                new SupplyPrice(1L, 21, LocalDate.parse("2026-10-01"), new BigDecimal("9000")),
                new SupplyPrice(2L, 21, LocalDate.parse("2026-11-10"), new BigDecimal("9500")));
        assertThat(Costs.unitCost(supplies.get(21L), ps, supplies, D)).isEqualByComparingTo("9");
        assertThat(Costs.unitCost(supplies.get(21L), ps, supplies, null)).isEqualByComparingTo("9.5");
    }

    @Test
    void latestPriceTieOnDateTakesHighestId() {
        List<SupplyPrice> ps = List.of(
                new SupplyPrice(5L, 21, D, new BigDecimal("1")),
                new SupplyPrice(7L, 21, D, new BigDecimal("2")),
                new SupplyPrice(6L, 21, D, new BigDecimal("3")));
        assertThat(Costs.latestPrice(21, ps, null)).map(SupplyPrice::id).hasValue(7L);
    }

    @Test
    void withoutPriceCostIsZero() {
        assertThat(Costs.unitCost(supplies.get(21L), List.of(), supplies, null)).isEqualByComparingTo("0");
    }

    @Test
    void compositesAverageAndMax() {
        assertThat(costs.get(30L)).isEqualByComparingTo("2.5");
        assertThat(costs.get(31L)).isEqualByComparingTo("2.5");
    }

    @Test
    void redCabbageGramsAndUnits() {
        assertNear(Costs.lineCost(l(18, "60"), costs, supplies), 150);
        assertNear(Costs.lineCost(l(18, "0.25", Unit.U), costs, supplies), 625);
    }

    @Test
    void lineCostMultipliesByPortions() {
        RecipeLine line = new RecipeLine(8, new BigDecimal("2"), Unit.U, new BigDecimal("2"));
        assertNear(Costs.lineCost(line, costs, supplies), 666.67);
    }

    @Test
    void raviolesDish() {
        assertNear(Costs.dishCost(ravioles, costs, supplies), 3166.5);
    }

    @Test
    void canelonesDish() {
        assertNear(Costs.dishCost(canelones, costs, supplies), 3202.18);
    }

    @Test
    void packCostAddsRecipesOilAndPackaging() {
        PackCost c = Costs.packCost(List.of(new MenuSlot(1, 1), new MenuSlot(4, 4)), Map.of(1L, ravioles, 4L, canelones),
                costs, supplies, settingsWithOil(19L),
                new PackagingCosts(new BigDecimal("750"), new BigDecimal("1500")));
        assertNear(c.recipes(), 6368.68);
        assertNear(c.oil(), 64);
        assertThat(c.packaging()).isEqualByComparingTo("5250");
        assertNear(c.direct(), 6368.68 + 64 + 5250);
    }

    @Test
    void missingDishAddsZero() {
        PackCost c = Costs.packCost(List.of(new MenuSlot(1, 99)), Map.of(), costs, supplies, settingsWithOil(null),
                new PackagingCosts(BigDecimal.ZERO, BigDecimal.ZERO));
        assertThat(c.recipes()).isEqualByComparingTo("0");
    }

    @Test
    void packagingCostsFromMarkedSupplies() {
        Supply tray = new Supply(40L, "Bandeja", Unit.U, 1, null, new BigDecimal("100"), null, PackagingType.TRAY,
                null, true, null);
        PackagingCosts p = Costs.packagingCosts(List.of(tray), Map.of(40L, new BigDecimal("750")));
        assertThat(p.tray()).isEqualByComparingTo("750");
        assertThat(p.sticker()).isEqualByComparingTo("0");
    }

    @Test
    void unitLineOnGramSupplyWithoutGramsPerUnitCountsOneGram() {
        // Pollo (21, g, sin gramsPerUnit): 2 u → 2 g × 9.5 = 19
        assertNear(Costs.lineCost(new RecipeLine(21, new BigDecimal("2"), Unit.U, null), costs, supplies), 19, 0.0001);
    }

    @Test
    void noOilAndNoPackagingSuppliesCostZero() {
        PackCost c = Costs.packCost(List.of(), Map.of(), costs, supplies, settingsWithOil(null),
                Costs.packagingCosts(List.of(), costs));
        assertThat(c.oil()).isEqualByComparingTo("0");
        assertThat(c.packaging()).isEqualByComparingTo("0");
    }
}
