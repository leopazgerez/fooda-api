package com.fooda.api.shared.infrastructure.in.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every error body: a client-safe message, the HTTP status, when it happened, and optionally the request field at
 * fault and structured extras (ids, quantities). {@code field} and {@code details} are left out when empty.
 */
public record ErrorResponse(String message, int status, Instant timestamp,
                            @JsonInclude(JsonInclude.Include.NON_EMPTY) String field,
                            @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> details) {

    public ErrorResponse {
        details = details == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(details));
    }

    public static ErrorResponse of(HttpStatusCode status, String message, Clock clock) {
        return new ErrorResponse(message, status.value(), clock.instant(), null, Map.of());
    }

    public ErrorResponse withField(String field) {
        return new ErrorResponse(message, status, timestamp, field, details);
    }

    /** As a response with this status, always as {@code application/json} whatever the request accepts. */
    public ResponseEntity<ErrorResponse> toResponseEntity() {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(this);
    }
}
