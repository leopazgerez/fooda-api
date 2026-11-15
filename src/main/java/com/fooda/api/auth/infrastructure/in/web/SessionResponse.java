package com.fooda.api.auth.infrastructure.in.web;

import com.fooda.api.auth.application.port.in.Session;
import com.fooda.api.user.infrastructure.in.web.UserResponse;

import java.time.Instant;

public record SessionResponse(String accessToken, Instant accessExpiresAt, String refreshToken, UserResponse user) {

    static SessionResponse from(Session s) {
        return new SessionResponse(s.accessToken(), s.accessExpiresAt(), s.refreshToken(), UserResponse.from(s.user()));
    }
}
