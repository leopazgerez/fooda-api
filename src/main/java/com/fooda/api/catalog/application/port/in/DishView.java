package com.fooda.api.catalog.application.port.in;

import com.fooda.api.core.domain.catalog.Dish;

import java.math.BigDecimal;
import java.util.List;

/** menus: the dish's positions in each pack's version current today. */
public record DishView(Dish dish, BigDecimal cost, List<MenuRef> menus) {

    public record MenuRef(int number, long packId) {
    }
}
