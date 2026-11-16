package com.fooda.api.core.domain.production;

import com.fooda.api.core.domain.catalog.Unit;

import java.math.BigDecimal;

public record ShoppingItem(long supplyId, String name, BigDecimal quantity, Unit unit, BigDecimal unitCost,
                           BigDecimal total) {
}
