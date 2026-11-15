package com.fooda.api.auth.infrastructure.out.persistence;

import com.fooda.api.auth.application.port.out.RefreshTokens;
import com.fooda.api.auth.application.port.out.StoredRefreshToken;
import com.fooda.api.user.application.port.out.UserSessions;
import com.fooda.api.user.domain.UserId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Component
public class JpaRefreshTokens implements RefreshTokens, UserSessions {

    private final RefreshTokenJpaRepository repository;
    private final Clock clock;

    public JpaRefreshTokens(RefreshTokenJpaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void save(UserId userId, String tokenHash, Instant expiresAt) {
        RefreshTokenJpaEntity e = new RefreshTokenJpaEntity();
        e.userId = userId.value();
        e.tokenHash = tokenHash;
        e.expiresAt = expiresAt;
        repository.saveAndFlush(e);
    }

    @Override
    @Transactional
    public Optional<StoredRefreshToken> lockActive(String tokenHash, Instant now) {
        return repository.lockActive(tokenHash, now).map(e -> new StoredRefreshToken(e.id, new UserId(e.userId)));
    }

    @Override
    @Transactional
    public void revoke(long id, Instant now) {
        repository.revoke(id, now);
    }

    @Override
    @Transactional
    public void revokeAll(UserId userId, Instant now) {
        repository.revokeAll(userId.value(), now);
    }

    @Override
    @Transactional
    public void revokeAll(UserId userId) {
        revokeAll(userId, clock.instant());
    }
}
