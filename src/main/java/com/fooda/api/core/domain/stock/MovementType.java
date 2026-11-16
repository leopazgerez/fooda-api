package com.fooda.api.core.domain.stock;

import com.fooda.api.shared.domain.ValidationException;

public enum MovementType {
    PRODUCTION("production"), TRANSFER_IN("transfer_in"), TRANSFER_OUT("transfer_out"), SALE("sale"), ADJUSTMENT("adjustment");

    private final String code;

    MovementType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static MovementType of(String code, String field) {
        for (MovementType v : values()) {
            if (v.code.equals(code)) {
                return v;
            }
        }
        throw new ValidationException(field, "Valor inválido");
    }
}
