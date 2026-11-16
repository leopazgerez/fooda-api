package com.fooda.api.core.domain.expense;

import com.fooda.api.shared.domain.ValidationException;

public enum PaymentMethod {
    TRANSFER("transfer"), CASH("cash"), MERCADO_PAGO("mercado_pago"), OTHER("other"), PACKS("packs");

    private final String code;

    PaymentMethod(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static PaymentMethod of(String code, String field) {
        for (PaymentMethod v : values()) {
            if (v.code.equals(code)) {
                return v;
            }
        }
        throw new ValidationException(field, "Valor inválido");
    }
}
