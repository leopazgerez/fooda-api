package com.fooda.api.auth.application.port.out;

import com.fooda.api.auth.domain.TokenType;
import com.fooda.api.user.domain.UserId;

import java.time.Instant;
import java.util.Optional;

public interface UserTokens {

    void save(UserId userId, TokenType type, String tokenHash, Instant expiresAt);

    void invalidateAll(UserId userId, TokenType type, Instant now);

    /** Single use: locks the row, requires it unused and unexpired, and marks it used. */
    Optional<UserId> consume(TokenType type, String tokenHash, Instant now);
}
