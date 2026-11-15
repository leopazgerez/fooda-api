package com.fooda.api.auth.application.service;

import com.fooda.api.auth.application.port.out.AccessTokenIssuer;
import com.fooda.api.auth.application.port.out.IssuedToken;
import com.fooda.api.user.domain.User;

import java.time.Instant;

public class FakeAccessTokenIssuer implements AccessTokenIssuer {

    @Override
    public IssuedToken issue(User user) {
        return new IssuedToken("access-" + user.email(), Instant.parse("2030-01-01T00:00:00Z"));
    }
}
