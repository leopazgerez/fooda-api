package com.example.app.auth.application.exception;

import com.example.app.shared.domain.ValidationException;

public class InvalidLinkException extends ValidationException {

    public InvalidLinkException() {
        super("token", "El enlace no es válido o ya venció");
    }
}
