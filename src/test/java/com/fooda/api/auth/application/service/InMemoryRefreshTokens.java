package com.fooda.api.auth.application.service;

import com.fooda.api.auth.application.port.out.RefreshTokens;
import com.fooda.api.auth.application.port.out.StoredRefreshToken;
import com.fooda.api.user.domain.UserId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class InMemoryRefreshTokens implements RefreshTokens {

    public static final class Row {
        public final long id;
        public final UserId userId;
        public final String hash;
        public final Instant expiresAt;
        public Instant revokedAt;

        Row(long id, UserId userId, String hash, Instant expiresAt) {
            this.id = id;
            this.userId = userId;
            this.hash = hash;
            this.expiresAt = expiresAt;
        }
    }

    public final List<Row> rows = new ArrayList<>();

    @Override
    public void save(UserId userId, String tokenHash, Instant expiresAt) {
        rows.add(new Row(rows.size() + 1, userId, tokenHash, expiresAt));
    }

    @Override
    public Optional<StoredRefreshToken> lockActive(String tokenHash, Instant now) {
        return rows.stream()
                .filter(r -> r.hash.equals(tokenHash) && r.revokedAt == null && r.expiresAt.isAfter(now))
                .findFirst().map(r -> new StoredRefreshToken(r.id, r.userId));
    }

    @Override
    public void revoke(long id, Instant now) {
        rows.stream().filter(r -> r.id == id && r.revokedAt == null).forEach(r -> r.revokedAt = now);
    }

    @Override
    public void revokeAll(UserId userId, Instant now) {
        rows.stream().filter(r -> r.userId.equals(userId) && r.revokedAt == null).forEach(r -> r.revokedAt = now);
    }
}
