package com.fooda.api.core.domain.catalog;

import com.fooda.api.shared.domain.ValidationException;

public record Supplier(Long id, String name, String purchaseDay, int sortOrder, Long version) {

    public Supplier {
        name = name == null ? "" : name.strip();
        if (name.isEmpty()) {
            throw new ValidationException("name", "Completá nombre");
        }
    }
}
