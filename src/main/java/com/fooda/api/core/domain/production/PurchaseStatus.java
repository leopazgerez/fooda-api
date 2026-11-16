package com.fooda.api.core.domain.production;

import com.fooda.api.shared.domain.ValidationException;

public enum PurchaseStatus {
    PENDING("pending"), BOUGHT("bought");

    private final String code;

    PurchaseStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static PurchaseStatus of(String code, String field) {
        for (PurchaseStatus v : values()) {
            if (v.code.equals(code)) {
                return v;
            }
        }
        throw new ValidationException(field, "Valor inválido");
    }
}
