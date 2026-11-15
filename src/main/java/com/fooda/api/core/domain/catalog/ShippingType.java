package com.fooda.api.core.domain.catalog;

import com.fooda.api.shared.domain.ValidationException;

public enum ShippingType {
    AMOUNT("amount"), PERCENTAGE("percentage");

    private final String code;

    ShippingType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ShippingType of(String code, String field) {
        for (ShippingType v : values()) {
            if (v.code.equals(code)) {
                return v;
            }
        }
        throw new ValidationException(field, "Valor inválido");
    }
}
