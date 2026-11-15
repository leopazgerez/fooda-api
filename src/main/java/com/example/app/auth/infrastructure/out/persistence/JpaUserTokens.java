package com.example.app.auth.infrastructure.out.persistence;

import com.example.app.auth.application.port.out.UserTokens;
import com.example.app.auth.domain.TokenType;
import com.example.app.user.domain.UserId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Component
public class JpaUserTokens implements UserTokens {

    private final UserTokenJpaRepository repository;

    public JpaUserTokens(UserTokenJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void save(UserId userId, TokenType type, String tokenHash, Instant expiresAt) {
        UserTokenJpaEntity e = new UserTokenJpaEntity();
        e.userId = userId.value();
        e.type = type;
        e.tokenHash = tokenHash;
        e.expiresAt = expiresAt;
        repository.saveAndFlush(e);
    }

    @Override
    @Transactional
    public void invalidateAll(UserId userId, TokenType type, Instant now) {
        repository.invalidateAll(userId.value(), type, now);
    }

    @Override
    @Transactional
    public Optional<UserId> consume(TokenType type, String tokenHash, Instant now) {
        return repository.lockUsable(type, tokenHash, now).map(e -> {
            e.usedAt = now;
            return new UserId(e.userId);
        });
    }
}
