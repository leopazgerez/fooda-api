package com.fooda.api.core.domain.production;

import com.fooda.api.core.domain.catalog.Supplier;

import java.math.BigDecimal;
import java.util.List;

public record ShoppingGroup(Supplier supplier, List<ShoppingItem> items, BigDecimal total) {

    public ShoppingGroup {
        items = List.copyOf(items);
    }
}
