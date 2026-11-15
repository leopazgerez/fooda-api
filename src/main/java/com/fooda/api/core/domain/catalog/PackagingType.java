package com.fooda.api.core.domain.catalog;

import com.fooda.api.shared.domain.ValidationException;

public enum PackagingType {
    TRAY("tray"), STICKER("sticker");

    private final String code;

    PackagingType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static PackagingType of(String code, String field) {
        for (PackagingType v : values()) {
            if (v.code.equals(code)) {
                return v;
            }
        }
        throw new ValidationException(field, "Valor inválido");
    }
}
