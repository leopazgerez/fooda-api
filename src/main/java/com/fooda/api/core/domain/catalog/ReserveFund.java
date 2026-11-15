package com.fooda.api.core.domain.catalog;

import com.fooda.api.shared.domain.ValidationException;

import java.math.BigDecimal;

public record ReserveFund(Long id, String name, BigDecimal percentage, Long version) {

    public ReserveFund {
        name = name == null ? "" : name.strip();
        if (name.isEmpty()) {
            throw new ValidationException("name", "Completá nombre");
        }
        if (percentage == null || percentage.signum() < 0) {
            throw new ValidationException("percentage", "El porcentaje debe ser un número ≥ 0");
        }
    }
}
