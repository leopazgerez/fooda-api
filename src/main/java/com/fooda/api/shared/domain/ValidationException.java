package com.fooda.api.shared.domain;

/** A request broke a rule about its own content (400). Messages are written for the client and safe to show. */
public class ValidationException extends RuntimeException {

    private final String field;

    public ValidationException(String message) {
        this(null, message);
    }

    public ValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** The request parameter or body property at fault, or null when the message alone says it. */
    public String field() {
        return field;
    }
}
