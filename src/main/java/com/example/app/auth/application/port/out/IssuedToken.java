package com.example.app.auth.application.port.out;

import java.time.Instant;

public record IssuedToken(String value, Instant expiresAt) {
}
