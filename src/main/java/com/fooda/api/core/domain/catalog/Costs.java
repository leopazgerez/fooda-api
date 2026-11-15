package com.fooda.api.core.domain.catalog;

import com.fooda.api.shared.domain.Decimals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Port de fooda-admin/src/lib/domain/costos.ts (+ costosEnvases), en BigDecimal y sin redondeos intermedios. */
public final class Costs {

    private Costs() {
    }

    /** Último precio con fecha ≤ {@code until} (sin límite si es nulo); empate de fecha → mayor id. */
    public static Optional<SupplyPrice> latestPrice(long supplyId, List<SupplyPrice> prices, LocalDate until) {
        return prices.stream()
                .filter(p -> p.supplyId() == supplyId && (until == null || !p.date().isAfter(until)))
                .max(Comparator.comparing(SupplyPrice::date).thenComparing(SupplyPrice::id));
    }

    /** Costo de un compuesto a partir del de sus partes: el máximo o el promedio. */
    public static BigDecimal combine(CompositeRule rule, List<BigDecimal> parts) {
        if (parts.isEmpty()) {
            return BigDecimal.ZERO;
        }
        if (rule == CompositeRule.MAX) {
            return parts.stream().max(Comparator.naturalOrder()).orElseThrow();
        }
        return parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(parts.size()), Decimals.MC);
    }

    /** Cantidad en unidad base del insumo (una línea en "u" de un insumo en g/ml usa gramsPerUnit, o 1). */
    public static BigDecimal toBase(Supply supply, BigDecimal quantity, Unit unit) {
        if (unit == Unit.U && supply != null && supply.unit() != Unit.U) {
            return quantity.multiply(supply.gramsPerUnit() != null ? supply.gramsPerUnit() : BigDecimal.ONE);
        }
        return quantity;
    }

    /** Costo por unidad base. Compuesto: promedio o máximo de sus partes. Sin precio → 0. */
    public static BigDecimal unitCost(Supply supply, List<SupplyPrice> prices, Map<Long, Supply> supplies,
                                      LocalDate until) {
        if (supply.composite() != null) {
            List<BigDecimal> parts = supply.composite().partIds().stream()
                    .map(id -> supplies.containsKey(id) ? unitCost(supplies.get(id), prices, supplies, until)
                            : BigDecimal.ZERO)
                    .toList();
            return combine(supply.composite().rule(), parts);
        }
        return latestPrice(supply.id(), prices, until)
                .map(p -> p.packagePrice().divide(supply.content(), Decimals.MC))
                .orElse(BigDecimal.ZERO);
    }

    public static Map<Long, BigDecimal> currentCosts(Collection<Supply> supplies, List<SupplyPrice> prices,
                                                     LocalDate until) {
        Map<Long, Supply> byId = new HashMap<>();
        supplies.forEach(s -> byId.put(s.id(), s));
        Map<Long, BigDecimal> costs = new HashMap<>();
        supplies.forEach(s -> costs.put(s.id(), unitCost(s, prices, byId, until)));
        return costs;
    }

    /** porciones × cantidad en base × costo. */
    public static BigDecimal lineCost(RecipeLine line, Map<Long, BigDecimal> costs, Map<Long, Supply> supplies) {
        return line.portions()
                .multiply(toBase(supplies.get(line.supplyId()), line.quantity(), line.unit()))
                .multiply(costs.getOrDefault(line.supplyId(), BigDecimal.ZERO));
    }

    public static BigDecimal dishCost(Dish dish, Map<Long, BigDecimal> costs, Map<Long, Supply> supplies) {
        return dish.recipe().stream()
                .map(l -> lineCost(l, costs, supplies))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Costo por unidad de bandeja y sticker según los insumos marcados como envase (0 si no hay). */
    public static PackagingCosts packagingCosts(Collection<Supply> supplies, Map<Long, BigDecimal> costs) {
        return new PackagingCosts(packaging(supplies, costs, PackagingType.TRAY),
                packaging(supplies, costs, PackagingType.STICKER));
    }

    private static BigDecimal packaging(Collection<Supply> supplies, Map<Long, BigDecimal> costs, PackagingType type) {
        return supplies.stream()
                .filter(s -> s.packagingType() == type)
                .findFirst()
                .map(s -> costs.getOrDefault(s.id(), BigDecimal.ZERO))
                .orElse(BigDecimal.ZERO);
    }

    public static PackCost packCost(List<MenuSlot> menus, Map<Long, Dish> dishes, Map<Long, BigDecimal> costs,
                                    Map<Long, Supply> supplies, Settings settings, PackagingCosts packaging) {
        BigDecimal recipes = menus.stream()
                .map(m -> dishes.get(m.dishId()))
                .map(d -> d == null ? BigDecimal.ZERO : dishCost(d, costs, supplies))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal oil = settings.oilSupplyId() == null || settings.oilMlPerPack() == null ? BigDecimal.ZERO
                : settings.oilMlPerPack().multiply(costs.getOrDefault(settings.oilSupplyId(), BigDecimal.ZERO));
        BigDecimal pack = BigDecimal.valueOf(settings.traysPerPack()).multiply(packaging.tray())
                .add(BigDecimal.valueOf(settings.stickersPerPack()).multiply(packaging.sticker()));
        return new PackCost(recipes, oil, pack, recipes.add(oil).add(pack));
    }
}
