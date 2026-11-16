package com.fooda.api.core.domain.production;

import com.fooda.api.core.domain.catalog.Dish;
import com.fooda.api.core.domain.catalog.Settings;
import com.fooda.api.core.domain.catalog.Supply;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ProductionSnapshot(Map<Long, BigDecimal> costs, Map<Long, Dish> dishes, List<SnapshotPack> packs,
                                 Settings settings, List<Supply> supplies) {

    public ProductionSnapshot {
        costs = costs == null ? Map.of() : Map.copyOf(costs);
        dishes = dishes == null ? Map.of() : Map.copyOf(dishes);
        packs = packs == null ? List.of() : List.copyOf(packs);
        supplies = supplies == null ? List.of() : List.copyOf(supplies);
    }

    public static ProductionSnapshot empty(Settings settings) {
        return new ProductionSnapshot(null, null, null, settings, null);
    }
}
