package com.fooda.api.core.domain.production;

import com.fooda.api.core.domain.catalog.CompositeRule;
import com.fooda.api.core.domain.catalog.Costs;
import com.fooda.api.core.domain.catalog.Dish;
import com.fooda.api.core.domain.catalog.MenuSlot;
import com.fooda.api.core.domain.catalog.PackVersion;
import com.fooda.api.core.domain.catalog.PackVersions;
import com.fooda.api.core.domain.catalog.RecipeLine;
import com.fooda.api.core.domain.catalog.Settings;
import com.fooda.api.core.domain.catalog.Supplier;
import com.fooda.api.core.domain.catalog.Supply;
import com.fooda.api.core.domain.catalog.SupplyPrice;
import com.fooda.api.shared.domain.Decimals;
import com.fooda.api.shared.domain.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Port de fooda-admin/src/lib/domain/produccion.ts (+ montoCompra de costos.ts), en BigDecimal. */
public final class ProductionRules {

    private static final String[] MONTHS =
            {"ENE", "FEB", "MAR", "ABR", "MAY", "JUN", "JUL", "AGO", "SEP", "OCT", "NOV", "DIC"};
    private static final Collator NAMES = Collator.getInstance(Locale.forLanguageTag("es"));

    private ProductionRules() {
    }

    /** lotCode(1, 2026-11-03) = "1 NOV 26". */
    public static String lotCode(int number, LocalDate date) {
        return "%d %s %02d".formatted(number, MONTHS[date.getMonthValue() - 1], date.getYear() % 100);
    }

    /** Versión vigente de cada pack a la fecha, platos referenciados, costos, insumos y settings. */
    public static ProductionSnapshot takeSnapshot(List<Long> packIds, List<PackVersion> versions, List<Dish> dishes,
                                                  List<Supply> supplies, List<SupplyPrice> prices, Settings settings,
                                                  LocalDate date) {
        List<SnapshotPack> packs = packIds.stream().map(packId -> {
            PackVersion v = PackVersions.current(versions, packId, date).orElseThrow(() -> new ValidationException(
                    "lines", "El pack " + packId + " no tiene versión vigente al " + date));
            return new SnapshotPack(packId, v.id(), v.menus());
        }).toList();
        Set<Long> used = packs.stream().flatMap(p -> p.menus().stream()).map(MenuSlot::dishId)
                .collect(Collectors.toSet());
        Map<Long, Dish> copy = dishes.stream().filter(d -> used.contains(d.id()))
                .collect(Collectors.toMap(Dish::id, Function.identity()));
        return new ProductionSnapshot(Costs.currentCosts(supplies, prices, date), copy, packs, settings, supplies);
    }

    /** Insumos del snapshot con el proveedor vivo; sin copia (lote inicial) se usan los vivos. */
    public static List<Supply> suppliesOf(Production p, List<Supply> live) {
        List<Supply> frozen = p.snapshot() == null ? List.of() : p.snapshot().supplies();
        if (frozen.isEmpty()) {
            return live;
        }
        Map<Long, Supply> liveById = byId(live);
        return frozen.stream().map(s -> {
            Supply v = liveById.get(s.id());
            return v == null || v.supplierId() == s.supplierId() ? s
                    : new Supply(s.id(), s.name(), s.unit(), v.supplierId(), s.presentation(), s.content(),
                    s.gramsPerUnit(), s.packagingType(), s.composite(), s.active(), s.version());
        }).toList();
    }

    public static PackCostInProduction packCost(Production p, long packId, List<Supply> live) {
        SnapshotPack sp = p.snapshotPack(packId).orElse(null);
        if (sp == null) {
            return new PackCostInProduction(BigDecimal.ZERO, BigDecimal.ZERO);
        }
        List<Supply> supplies = suppliesOf(p, live);
        Map<Long, Supply> byId = byId(supplies);
        Map<Long, BigDecimal> estimated = p.snapshot().costs();
        Map<Long, BigDecimal> actual = new HashMap<>(estimated);
        actual.putAll(p.paidCosts());
        Function<Map<Long, BigDecimal>, BigDecimal> calc = c -> Costs.packCost(sp.menus(), p.snapshot().dishes(), c,
                byId, p.snapshot().settings(), Costs.packagingCosts(supplies, c)).direct();
        return new PackCostInProduction(calc.apply(estimated), calc.apply(actual));
    }

    /** Lista de súper por proveedor, menos sobrantes, sin envases. */
    public static List<ShoppingGroup> shoppingList(Production p, List<Supply> live, List<Supplier> suppliers) {
        Map<Long, Supply> byId = byId(suppliesOf(p, live));
        Map<Long, BigDecimal> costs = p.snapshot().costs();
        Map<Long, BigDecimal> quantities = new LinkedHashMap<>();
        for (ProductionLine line : p.lines()) {
            for (MenuSlot m : p.snapshotPack(line.packId()).map(SnapshotPack::menus).orElse(List.of())) {
                Dish dish = p.snapshot().dishes().get(m.dishId());
                for (RecipeLine r : dish == null ? List.<RecipeLine>of() : dish.recipe()) {
                    Supply supply = byId.get(r.supplyId());
                    if (supply == null) {
                        continue;
                    }
                    BigDecimal total = Costs.toBase(supply, scaled(r, line.planned()), r.unit());
                    expand(supply, total, byId, costs).forEach((id, q) -> quantities.merge(id, q, BigDecimal::add));
                }
            }
        }
        Map<Long, List<ShoppingItem>> groups = new HashMap<>();
        quantities.forEach((id, q) -> {
            Supply supply = byId.get(id);
            BigDecimal quantity = q.subtract(p.leftovers().getOrDefault(id, BigDecimal.ZERO)).max(BigDecimal.ZERO);
            if (supply.packagingType() != null || quantity.signum() <= 0) {
                return;
            }
            BigDecimal unitCost = costs.getOrDefault(id, BigDecimal.ZERO);
            groups.computeIfAbsent(supply.supplierId(), k -> new ArrayList<>()).add(new ShoppingItem(id,
                    supply.name(), quantity, supply.unit(), unitCost, quantity.multiply(unitCost)));
        });
        return suppliers.stream()
                .sorted(Comparator.comparingInt(Supplier::sortOrder))
                .filter(s -> groups.containsKey(s.id()))
                .map(s -> {
                    List<ShoppingItem> items = groups.get(s.id()).stream()
                            .sorted(Comparator.comparing(ShoppingItem::name, NAMES)).toList();
                    return new ShoppingGroup(s, items,
                            items.stream().map(ShoppingItem::total).reduce(BigDecimal.ZERO, BigDecimal::add));
                })
                .toList();
    }

    /** Reparte un compuesto: AVERAGE en partes iguales; MAX todo a la parte más cara del snapshot. */
    private static Map<Long, BigDecimal> expand(Supply supply, BigDecimal quantity, Map<Long, Supply> byId,
                                                Map<Long, BigDecimal> costs) {
        if (supply.composite() == null) {
            return Map.of(supply.id(), quantity);
        }
        List<Supply> parts = supply.composite().partIds().stream().filter(byId::containsKey).map(byId::get).toList();
        if (parts.isEmpty()) {
            return Map.of();
        }
        if (supply.composite().rule() == CompositeRule.MAX) {
            Supply dearest = parts.get(0);
            for (Supply s : parts) {
                if (cost(costs, s).compareTo(cost(costs, dearest)) > 0) {
                    dearest = s;
                }
            }
            return Map.of(dearest.id(), quantity);
        }
        BigDecimal each = quantity.divide(BigDecimal.valueOf(parts.size()), Decimals.MC);
        Map<Long, BigDecimal> out = new LinkedHashMap<>();
        parts.forEach(s -> out.merge(s.id(), each, BigDecimal::add));
        return out;
    }

    /** Recetario por pack → menú, multiplicado por los packs planificados (sin conversión de unidades). */
    public static List<ScaledPack> recipeBook(Production p, List<Supply> live) {
        Map<Long, Supply> byId = byId(suppliesOf(p, live));
        return p.lines().stream().map(line -> {
            List<ScaledMenu> menus = p.snapshotPack(line.packId()).map(SnapshotPack::menus).orElse(List.of())
                    .stream().map(m -> {
                        Dish dish = p.snapshot().dishes().get(m.dishId());
                        ProductionLine other = p.lines().stream()
                                .filter(o -> o.packId() != line.packId() && p.snapshotPack(o.packId())
                                        .map(sp -> sp.menus().stream().anyMatch(om -> om.dishId() == m.dishId()))
                                        .orElse(false))
                                .findFirst().orElse(null);
                        List<ScaledMenu.Ingredient> ingredients = dish == null ? List.of() : dish.recipe().stream()
                                .map(r -> new ScaledMenu.Ingredient(
                                        byId.containsKey(r.supplyId()) ? byId.get(r.supplyId()).name() : "",
                                        scaled(r, line.planned()), r.unit()))
                                .toList();
                        return new ScaledMenu(m.number(), dish == null ? "" : dish.name(), ingredients,
                                other == null ? null : new ScaledMenu.SharedWith(other.packId(), other.planned()),
                                other == null ? null : line.planned() + other.planned());
                    }).toList();
            return new ScaledPack(line.packId(), line.planned(), menus);
        }).toList();
    }

    /** Σ cantidad ÷ contenido × precio del envase (contenido 1 si el insumo no está), a entero HALF_UP. */
    public static BigDecimal purchaseAmount(List<Supply> supplies, List<PurchaseLine> lines) {
        Map<Long, Supply> byId = byId(supplies);
        return lines.stream()
                .map(l -> {
                    Supply s = byId.get(l.supplyId());
                    BigDecimal content = s == null ? BigDecimal.ONE : s.content();
                    return l.quantity().divide(content, Decimals.MC).multiply(l.packagePrice());
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(0, RoundingMode.HALF_UP);
    }

    private static BigDecimal scaled(RecipeLine r, int planned) {
        return r.quantity().multiply(r.portions()).multiply(BigDecimal.valueOf(planned));
    }

    private static BigDecimal cost(Map<Long, BigDecimal> costs, Supply s) {
        return costs.getOrDefault(s.id(), BigDecimal.ZERO);
    }

    private static Map<Long, Supply> byId(List<Supply> supplies) {
        Map<Long, Supply> out = new HashMap<>();
        supplies.forEach(s -> out.putIfAbsent(s.id(), s));
        return out;
    }
}
