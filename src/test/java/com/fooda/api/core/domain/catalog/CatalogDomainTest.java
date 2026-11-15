package com.fooda.api.core.domain.catalog;

import com.fooda.api.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogDomainTest {

    private static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    private static void invalid(Runnable r, String field, String message) {
        assertThatThrownBy(r::run).isInstanceOfSatisfying(ValidationException.class, e -> {
            assertThat(e.field()).isEqualTo(field);
            assertThat(e.getMessage()).isEqualTo(message);
        });
    }

    private static Supply supply(Long id, String name, BigDecimal content, BigDecimal grams, Composite c) {
        return new Supply(id, name, Unit.G, 1, "bolsa", content, grams, null, c, true, null);
    }

    private static Shipping ship() {
        return new Shipping(ShippingType.AMOUNT, d("0"));
    }

    private static Settings settings(BigDecimal price, List<String> types) {
        return new Settings(price, d("10"), null, 5, 5, d("1"), 3, 30, d("10"), List.of("a"), types, null);
    }

    @Test void unitCodeRoundTrip() {
        assertThat(Unit.of("ml", "unit")).isEqualTo(Unit.ML);
        assertThat(Unit.ML.code()).isEqualTo("ml");
        assertThat(ShippingType.of("percentage", "t")).isEqualTo(ShippingType.PERCENTAGE);
        assertThat(PackagingType.of("tray", "t")).isEqualTo(PackagingType.TRAY);
        assertThat(CompositeRule.of("max", "t")).isEqualTo(CompositeRule.MAX);
    }

    @Test void enumUnknownOrNullCode() {
        invalid(() -> Unit.of("kg", "unit"), "unit", "Valor inválido");
        invalid(() -> Unit.of(null, "unit"), "unit", "Valor inválido");
        invalid(() -> ShippingType.of("x", "type"), "type", "Valor inválido");
        invalid(() -> PackagingType.of("x", "p"), "p", "Valor inválido");
        invalid(() -> CompositeRule.of("x", "r"), "r", "Valor inválido");
    }

    @Test void supplier() {
        invalid(() -> new Supplier(null, " ", null, 0, null), "name", "Completá nombre");
        assertThat(new Supplier(null, " x ", null, 0, null).name()).isEqualTo("x");
    }

    @Test void shipping() {
        String m = "El envío debe ser un monto o un porcentaje con valor ≥ 0";
        invalid(() -> new Shipping(null, d("1")), "shipping", m);
        invalid(() -> new Shipping(ShippingType.AMOUNT, null), "shipping", m);
        invalid(() -> new Shipping(ShippingType.AMOUNT, d("-1")), "shipping", m);
    }

    @Test void branch() {
        invalid(() -> new Branch(null, "", "a", 1, 1, 1, ship(), false, false, null), "name", "Completá nombre");
        invalid(() -> new Branch(null, "n", "a", -1, 1, 1, ship(), false, false, null), "capacity", "La capacidad debe ser un número ≥ 0");
        invalid(() -> new Branch(null, "n", "a", 1, -1, 1, ship(), false, false, null), "stockThreshold", "El umbral debe ser un número ≥ 0");
        invalid(() -> new Branch(null, "n", "a", 1, 1, -1, ship(), false, false, null), "freeSlotsThreshold", "El umbral debe ser un número ≥ 0");
        assertThat(new Branch(null, "n", null, 1, 1, 1, ship(), false, false, null).address()).isEmpty();
    }

    @Test void reserveFund() {
        invalid(() -> new ReserveFund(null, " ", d("1"), null), "name", "Completá nombre");
        invalid(() -> new ReserveFund(null, "n", null, null), "percentage", "El porcentaje debe ser un número ≥ 0");
        invalid(() -> new ReserveFund(null, "n", d("-1"), null), "percentage", "El porcentaje debe ser un número ≥ 0");
    }

    @Test void composite() {
        invalid(() -> new Composite(CompositeRule.AVERAGE, List.of()), "composite", "Un compuesto necesita al menos un insumo");
        List<Long> parts = new ArrayList<>(List.of(1L));
        Composite c = new Composite(CompositeRule.MAX, parts);
        parts.add(2L);
        assertThat(c.partIds()).containsExactly(1L);
    }

    @Test void supplyDefaultsAndRules() {
        invalid(() -> supply(1L, " ", d("1"), null, null), "name", "Ingresá el nombre del insumo");
        invalid(() -> supply(1L, "n", null, null, null), "content", "El contenido del envase debe ser mayor a cero");
        invalid(() -> supply(1L, "n", d("0"), null, null), "content", "El contenido del envase debe ser mayor a cero");
        invalid(() -> supply(1L, "n", d("1"), d("0"), null), "gramsPerUnit", "Los gramos por unidad deben ser mayores a cero");
        invalid(() -> supply(1L, "n", d("1"), null, new Composite(CompositeRule.MAX, List.of(1L))),
                "composite", "Un insumo no puede ser parte de sí mismo");
        assertThat(new Supply(null, "n", null, 1, null, d("1"), null, null, null, true, null).unit()).isEqualTo(Unit.G);
    }

    @Test void supplyPrice() {
        invalid(() -> new SupplyPrice(null, 1, LocalDate.now(), null), "packagePrice", "Ingresá un precio válido");
        invalid(() -> new SupplyPrice(null, 1, LocalDate.now(), d("-1")), "packagePrice", "Ingresá un precio válido");
    }

    @Test void recipeLine() {
        invalid(() -> new RecipeLine(1, null, Unit.G, null), "recipe", "Las cantidades deben ser mayores a cero");
        invalid(() -> new RecipeLine(1, d("0"), Unit.G, null), "recipe", "Las cantidades deben ser mayores a cero");
        assertThat(new RecipeLine(1, d("10"), Unit.G, null).portions()).isEqualByComparingTo("1");
        assertThat(new RecipeLine(1, d("10"), Unit.G, d("0")).portions()).isEqualByComparingTo("1");
    }

    @Test void dish() {
        invalid(() -> new Dish(null, " ", List.of(new RecipeLine(1, d("1"), Unit.G, null)), null), "name", "Ingresá el nombre del plato");
        invalid(() -> new Dish(null, "n", List.of(), null), "recipe", "La receta necesita al menos un ingrediente");
    }

    @Test void pack() {
        invalid(() -> new Pack(null, 1, " ", true, null, null), "name", "Ingresá el nombre del pack");
        invalid(() -> new Pack(null, 1, "n", true, d("0"), null), "price", "El precio debe ser mayor a cero");
    }

    @Test void packSalePriceFallsBackToSettings() {
        Settings s = settings(d("45000"), List.of("fijo"));
        assertThat(new Pack(null, 1, "n", true, null, null).salePrice(s)).isEqualByComparingTo("45000");
        assertThat(new Pack(null, 1, "n", true, d("50000"), null).salePrice(s)).isEqualByComparingTo("50000");
    }

    @Test void menuSlot() {
        invalid(() -> new MenuSlot(0, 1), "menus", "Cada menú necesita un número");
    }

    @Test void packVersion() {
        String m = "Un pack lleva exactamente 5 menús";
        invalid(() -> new PackVersion(null, 1, LocalDate.now(), null), "menus", m);
        invalid(() -> new PackVersion(null, 1, LocalDate.now(), List.of(new MenuSlot(1, 1))), "menus", m);
    }

    @Test void settings() {
        invalid(() -> settings(null, List.of("fijo")), "packPrice", "El precio del pack debe ser mayor a cero");
        invalid(() -> settings(d("0"), List.of("fijo")), "packPrice", "El precio del pack debe ser mayor a cero");
        invalid(() -> settings(d("1"), List.of("otro")), "expenseTypes",
                "El tipo \"fijo\" no se puede quitar: con él se calcula el indirecto por pack");
        assertThat(Settings.FIXED_EXPENSE_TYPE).isEqualTo("fijo");
    }
}
