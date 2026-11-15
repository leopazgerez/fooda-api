package com.fooda.api.auth.application.exception;

/** Login or session failed (401). */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        this("Email o contraseña incorrectos");
    }

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
