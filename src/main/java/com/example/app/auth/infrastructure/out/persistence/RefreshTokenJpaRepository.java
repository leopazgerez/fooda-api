package com.example.app.auth.infrastructure.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshTokenJpaEntity t where t.tokenHash = :hash and t.revokedAt is null and t.expiresAt > :now")
    Optional<RefreshTokenJpaEntity> lockActive(@Param("hash") String hash, @Param("now") Instant now);

    @Modifying
    @Query("update RefreshTokenJpaEntity t set t.revokedAt = :now where t.id = :id and t.revokedAt is null")
    void revoke(@Param("id") long id, @Param("now") Instant now);

    @Modifying
    @Query("update RefreshTokenJpaEntity t set t.revokedAt = :now where t.userId = :userId and t.revokedAt is null")
    void revokeAll(@Param("userId") long userId, @Param("now") Instant now);
}
