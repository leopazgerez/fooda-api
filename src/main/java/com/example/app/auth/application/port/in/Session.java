package com.example.app.auth.application.port.in;

import com.example.app.user.domain.User;

import java.time.Instant;

public record Session(String accessToken, Instant accessExpiresAt, String refreshToken, User user) {
}
