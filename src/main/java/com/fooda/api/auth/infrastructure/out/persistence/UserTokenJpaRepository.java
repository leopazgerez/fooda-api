package com.fooda.api.auth.infrastructure.out.persistence;

import com.fooda.api.auth.domain.TokenType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

interface UserTokenJpaRepository extends JpaRepository<UserTokenJpaEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from UserTokenJpaEntity t where t.type = :type and t.tokenHash = :hash and t.usedAt is null and t.expiresAt > :now")
    Optional<UserTokenJpaEntity> lockUsable(@Param("type") TokenType type, @Param("hash") String hash, @Param("now") Instant now);

    @Modifying
    @Query("update UserTokenJpaEntity t set t.usedAt = :now where t.userId = :userId and t.type = :type and t.usedAt is null")
    void invalidateAll(@Param("userId") long userId, @Param("type") TokenType type, @Param("now") Instant now);
}
