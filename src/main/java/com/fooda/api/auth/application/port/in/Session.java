package com.fooda.api.auth.application.port.in;

import com.fooda.api.user.domain.User;

import java.time.Instant;

public record Session(String accessToken, Instant accessExpiresAt, String refreshToken, User user) {
}
