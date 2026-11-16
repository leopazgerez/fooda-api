package com.fooda.api.core.domain.production;

import com.fooda.api.shared.domain.ValidationException;

public enum ProductionStatus {
    PLANNED("planned"), CLOSED("closed");

    private final String code;

    ProductionStatus(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static ProductionStatus of(String code, String field) {
        for (ProductionStatus v : values()) {
            if (v.code.equals(code)) {
                return v;
            }
        }
        throw new ValidationException(field, "Valor inválido");
    }
}
