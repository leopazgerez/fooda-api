package com.fooda.api.user.domain;

import com.fooda.api.shared.domain.ValidationException;

import java.nio.charset.StandardCharsets;

public final class Password {

    private Password() {
    }

    public static void check(String field, String raw) {
        if (raw == null || raw.length() < 8) {
            throw new ValidationException(field, "La contraseña debe tener al menos 8 caracteres");
        }
        if (raw.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ValidationException(field, "La contraseña puede tener como máximo 72 bytes");
        }
    }
}
