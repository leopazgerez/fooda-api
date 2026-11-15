package com.fooda.api.shared.infrastructure.in.web;

import org.springframework.security.oauth2.jwt.Jwt;

public final class Principals {

    private Principals() {
    }

    public static long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
