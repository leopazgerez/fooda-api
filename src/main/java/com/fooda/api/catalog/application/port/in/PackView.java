package com.fooda.api.catalog.application.port.in;

import com.fooda.api.core.domain.catalog.Pack;
import com.fooda.api.core.domain.catalog.PackVersion;

import java.math.BigDecimal;
import java.util.List;

/** currentVersion and cost are null when the pack has no version current today. */
public record PackView(Pack pack, BigDecimal salePrice, CurrentVersion currentVersion, CostView cost,
                       BigDecimal profit, BigDecimal margin) {

    public record CurrentVersion(PackVersion version, List<MenuView> menus) {
    }

    public record MenuView(int number, long dishId, String dishName, BigDecimal cost) {
    }

    public record CostView(BigDecimal recipes, BigDecimal oil, BigDecimal packaging, BigDecimal direct,
                           BigDecimal overhead, BigDecimal total) {
    }
}
