package com.example.app.auth.application.exception;

/** Correct credentials but the email is not verified yet (403). */
public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException() {
        super("Verificá tu email antes de iniciar sesión");
    }
}
