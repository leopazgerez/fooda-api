package com.fooda.api.catalog.application.port.in;

import com.fooda.api.core.domain.catalog.Supply;

import java.math.BigDecimal;

/** packagePrice is null when the supply has no price. */
public record SupplyView(Supply supply, String supplierName, BigDecimal unitCost, BigDecimal packagePrice) {
}
