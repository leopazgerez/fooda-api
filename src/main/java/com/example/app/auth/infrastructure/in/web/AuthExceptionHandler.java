package com.example.app.auth.infrastructure.in.web;

import com.example.app.auth.application.exception.EmailNotVerifiedException;
import com.example.app.auth.application.exception.InvalidCredentialsException;
import com.example.app.shared.infrastructure.in.web.ErrorResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Clock;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthExceptionHandler {

    private final Clock clock;

    public AuthExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ErrorResponse> invalidCredentials(InvalidCredentialsException e) {
        return ErrorResponse.of(HttpStatus.UNAUTHORIZED, e.getMessage(), clock).toResponseEntity();
    }

    @ExceptionHandler(EmailNotVerifiedException.class)
    ResponseEntity<ErrorResponse> emailNotVerified(EmailNotVerifiedException e) {
        return ErrorResponse.of(HttpStatus.FORBIDDEN, e.getMessage(), clock).toResponseEntity();
    }
}
