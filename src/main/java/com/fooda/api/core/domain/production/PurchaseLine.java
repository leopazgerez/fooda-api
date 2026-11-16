package com.fooda.api.core.domain.production;

import com.fooda.api.shared.domain.ValidationException;

import java.math.BigDecimal;

public record PurchaseLine(long supplyId, BigDecimal quantity, BigDecimal packagePrice) {

    public PurchaseLine {
        if (quantity == null || quantity.signum() <= 0 || packagePrice == null || packagePrice.signum() < 0) {
            throw new ValidationException("lines", "Revisá cantidades y precios");
        }
    }
}
