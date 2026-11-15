package com.fooda.api.auth.infrastructure.out.persistence;

import com.fooda.api.TestcontainersConfiguration;
import com.fooda.api.auth.domain.TokenType;
import com.fooda.api.user.domain.Role;
import com.fooda.api.user.domain.User;
import com.fooda.api.user.domain.UserId;
import com.fooda.api.user.infrastructure.out.persistence.UserPersistenceAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, UserPersistenceAdapter.class, JpaUserTokens.class})
class JpaUserTokensTest {

    static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired JpaUserTokens tokens;
    @Autowired UserPersistenceAdapter users;

    private UserId user() {
        return users.save(new User(null, "a@mail.com", "Ana", "h", Role.USER, false, true)).id();
    }

    @Test
    void consumeIsSingleUse() {
        UserId u = user();
        tokens.save(u, TokenType.VERIFY_EMAIL, "a".repeat(64), NOW.plusSeconds(60));
        assertThat(tokens.consume(TokenType.VERIFY_EMAIL, "a".repeat(64), NOW)).contains(u);
        assertThat(tokens.consume(TokenType.VERIFY_EMAIL, "a".repeat(64), NOW)).isEmpty();
    }

    @Test
    void consumeRejectsExpiredAndWrongType() {
        UserId u = user();
        tokens.save(u, TokenType.VERIFY_EMAIL, "a".repeat(64), NOW.minusSeconds(1));
        tokens.save(u, TokenType.RESET_PASSWORD, "b".repeat(64), NOW.plusSeconds(60));
        assertThat(tokens.consume(TokenType.VERIFY_EMAIL, "a".repeat(64), NOW)).isEmpty();
        assertThat(tokens.consume(TokenType.VERIFY_EMAIL, "b".repeat(64), NOW)).isEmpty();
    }

    @Test
    void invalidateAllMarksPreviousUsedOnlyOfSameType() {
        UserId u = user();
        tokens.save(u, TokenType.RESET_PASSWORD, "a".repeat(64), NOW.plusSeconds(60));
        tokens.save(u, TokenType.VERIFY_EMAIL, "b".repeat(64), NOW.plusSeconds(60));
        tokens.invalidateAll(u, TokenType.RESET_PASSWORD, NOW);
        assertThat(tokens.consume(TokenType.RESET_PASSWORD, "a".repeat(64), NOW)).isEmpty();
        assertThat(tokens.consume(TokenType.VERIFY_EMAIL, "b".repeat(64), NOW)).contains(u);
    }
}
