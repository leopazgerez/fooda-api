package com.fooda.api.core.domain.catalog;

import com.fooda.api.shared.domain.ValidationException;

public record Branch(Long id, String name, String address, int capacity, int stockThreshold, int freeSlotsThreshold,
                     Shipping shipping, boolean affectsPrice, boolean kitchen, Long version) {

    public Branch {
        name = name == null ? "" : name.strip();
        if (name.isEmpty()) {
            throw new ValidationException("name", "Completá nombre");
        }
        address = address == null ? "" : address.strip();
        if (capacity < 0) {
            throw new ValidationException("capacity", "La capacidad debe ser un número ≥ 0");
        }
        if (stockThreshold < 0) {
            throw new ValidationException("stockThreshold", "El umbral debe ser un número ≥ 0");
        }
        if (freeSlotsThreshold < 0) {
            throw new ValidationException("freeSlotsThreshold", "El umbral debe ser un número ≥ 0");
        }
    }
}
