package com.fooda.api.core.domain.catalog;

import com.fooda.api.shared.domain.ValidationException;

import java.math.BigDecimal;

public record RecipeLine(long supplyId, BigDecimal quantity, Unit unit, BigDecimal portions) {

    public RecipeLine {
        if (quantity == null || quantity.signum() <= 0) {
            throw new ValidationException("recipe", "Las cantidades deben ser mayores a cero");
        }
        if (portions == null || portions.signum() <= 0) {
            portions = BigDecimal.ONE;
        }
    }
}
