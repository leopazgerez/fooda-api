package com.fooda.api.core.domain.catalog;

import com.fooda.api.shared.domain.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SupplyPrice(Long id, long supplyId, LocalDate date, BigDecimal packagePrice) {

    public SupplyPrice {
        if (packagePrice == null || packagePrice.signum() < 0) {
            throw new ValidationException("packagePrice", "Ingresá un precio válido");
        }
    }
}
