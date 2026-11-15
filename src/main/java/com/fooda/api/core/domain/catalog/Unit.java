package com.fooda.api.core.domain.catalog;

import com.fooda.api.shared.domain.ValidationException;

public enum Unit {
    G("g"), ML("ml"), U("u");

    private final String code;

    Unit(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Unit of(String code, String field) {
        for (Unit v : values()) {
            if (v.code.equals(code)) {
                return v;
            }
        }
        throw new ValidationException(field, "Valor inválido");
    }
}
