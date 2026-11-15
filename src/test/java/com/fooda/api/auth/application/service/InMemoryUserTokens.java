package com.fooda.api.auth.application.service;

import com.fooda.api.auth.application.port.out.UserTokens;
import com.fooda.api.auth.domain.TokenType;
import com.fooda.api.user.domain.UserId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InMemoryUserTokens implements UserTokens {

    private static final class Row {
        UserId userId;
        TokenType type;
        String hash;
        Instant expiresAt;
        boolean used;
    }

    private final List<Row> rows = new ArrayList<>();

    @Override
    public void save(UserId userId, TokenType type, String tokenHash, Instant expiresAt) {
        Row r = new Row();
        r.userId = userId;
        r.type = type;
        r.hash = tokenHash;
        r.expiresAt = expiresAt;
        rows.add(r);
    }

    @Override
    public void invalidateAll(UserId userId, TokenType type, Instant now) {
        rows.stream().filter(r -> r.userId.equals(userId) && r.type == type).forEach(r -> r.used = true);
    }

    @Override
    public Optional<UserId> consume(TokenType type, String tokenHash, Instant now) {
        return rows.stream()
                .filter(r -> r.type == type && r.hash.equals(tokenHash) && !r.used && r.expiresAt.isAfter(now))
                .findFirst().map(r -> {
                    r.used = true;
                    return r.userId;
                });
    }
}
