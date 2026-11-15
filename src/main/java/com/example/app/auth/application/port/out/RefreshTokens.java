package com.example.app.auth.application.port.out;

import com.example.app.user.domain.UserId;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokens {

    void save(UserId userId, String tokenHash, Instant expiresAt);

    /** Locks the row of an unrevoked, unexpired token until the caller's transaction ends. */
    Optional<StoredRefreshToken> lockActive(String tokenHash, Instant now);

    void revoke(long id, Instant now);

    void revokeAll(UserId userId, Instant now);
}
