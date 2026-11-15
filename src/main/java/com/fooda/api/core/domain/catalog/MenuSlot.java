package com.fooda.api.core.domain.catalog;

import com.fooda.api.shared.domain.ValidationException;

public record MenuSlot(int number, long dishId) {

    public MenuSlot {
        if (number < 1) {
            throw new ValidationException("menus", "Cada menú necesita un número");
        }
    }
}
