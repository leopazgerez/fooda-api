package com.example.app.auth.application.port.out;

import com.example.app.auth.domain.TokenType;
import com.example.app.user.domain.UserId;

import java.time.Instant;
import java.util.Optional;

public interface UserTokens {

    void save(UserId userId, TokenType type, String tokenHash, Instant expiresAt);

    void invalidateAll(UserId userId, TokenType type, Instant now);

    /** Single use: locks the row, requires it unused and unexpired, and marks it used. */
    Optional<UserId> consume(TokenType type, String tokenHash, Instant now);
}
