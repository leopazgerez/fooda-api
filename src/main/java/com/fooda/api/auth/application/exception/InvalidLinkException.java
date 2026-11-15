package com.fooda.api.auth.application.exception;

import com.fooda.api.shared.domain.ValidationException;

public class InvalidLinkException extends ValidationException {

    public InvalidLinkException() {
        super("token", "El enlace no es válido o ya venció");
    }
}
