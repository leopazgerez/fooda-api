package com.example.app.auth.infrastructure.in.web;

import com.example.app.auth.application.port.in.Session;
import com.example.app.user.infrastructure.in.web.UserResponse;

import java.time.Instant;

public record SessionResponse(String accessToken, Instant accessExpiresAt, String refreshToken, UserResponse user) {

    static SessionResponse from(Session s) {
        return new SessionResponse(s.accessToken(), s.accessExpiresAt(), s.refreshToken(), UserResponse.from(s.user()));
    }
}
