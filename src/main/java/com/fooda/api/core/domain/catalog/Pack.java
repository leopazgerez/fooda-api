package com.fooda.api.core.domain.catalog;

import com.fooda.api.shared.domain.ValidationException;

import java.math.BigDecimal;

public record Pack(Long id, int number, String name, boolean active, BigDecimal price, Long version) {

    public Pack {
        name = name == null ? "" : name.strip();
        if (name.isEmpty()) {
            throw new ValidationException("name", "Ingresá el nombre del pack");
        }
        if (price != null && price.signum() <= 0) {
            throw new ValidationException("price", "El precio debe ser mayor a cero");
        }
    }

    public BigDecimal salePrice(Settings s) {
        return price != null ? price : s.packPrice();
    }
}
