package com.fooda.api.core.domain.sales;

import com.fooda.api.shared.domain.ValidationException;

public enum SaleType {
    RETAIL("retail"), WHOLESALE("wholesale"), CONSIGNMENT("consignment"), INTERNAL_USE("internal_use"),
    PAID_WITH_PACKS("paid_with_packs");

    private final String code;

    SaleType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static SaleType of(String code) {
        for (SaleType v : values()) {
            if (v.code.equals(code)) {
                return v;
            }
        }
        throw new ValidationException("type", "Tipo de venta inválido");
    }
}
