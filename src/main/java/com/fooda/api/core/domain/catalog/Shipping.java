package com.fooda.api.core.domain.catalog;

import com.fooda.api.shared.domain.ValidationException;

import java.math.BigDecimal;

public record Shipping(ShippingType type, BigDecimal value) {

    public Shipping {
        if (type == null || value == null || value.signum() < 0) {
            throw new ValidationException("shipping", "El envío debe ser un monto o un porcentaje con valor ≥ 0");
        }
    }
}
