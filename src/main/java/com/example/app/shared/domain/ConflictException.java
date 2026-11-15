package com.example.app.shared.domain;

/** A valid request that conflicts with the current state (409). Messages are safe to show. */
public class ConflictException extends RuntimeException {

    private final String field;

    public ConflictException(String message) {
        this(null, message);
    }

    public ConflictException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** The request property at fault, or null when the message alone says it. */
    public String field() {
        return field;
    }
}
