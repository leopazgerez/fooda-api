package com.fooda.api.auth.infrastructure.out.persistence;

import com.fooda.api.TestcontainersConfiguration;
import com.fooda.api.user.domain.Role;
import com.fooda.api.user.domain.User;
import com.fooda.api.user.domain.UserId;
import com.fooda.api.user.infrastructure.out.persistence.UserPersistenceAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JpaRefreshTokensTest {

    static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Autowired JpaRefreshTokens tokens;
    @Autowired UserPersistenceAdapter users;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager tm;

    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM refresh_token");
        jdbc.update("TRUNCATE app_user CASCADE");
    }

    private UserId user(String email) {
        return users.save(new User(null, email, "Ana", "h", Role.USER, true, true)).id();
    }

    @Test
    void lockActiveSkipsRevokedAndExpired() {
        UserId u = user("a@mail.com");
        tokens.save(u, "a".repeat(64), NOW.plusSeconds(60));
        tokens.save(u, "b".repeat(64), NOW.minusSeconds(1));
        tokens.save(u, "c".repeat(64), NOW.plusSeconds(60));
        tokens.revoke(tokens.lockActive("c".repeat(64), NOW).orElseThrow().id(), NOW);

        assertThat(tokens.lockActive("a".repeat(64), NOW)).isPresent();
        assertThat(tokens.lockActive("a".repeat(64), NOW).orElseThrow().userId()).isEqualTo(u);
        assertThat(tokens.lockActive("b".repeat(64), NOW)).isEmpty();
        assertThat(tokens.lockActive("c".repeat(64), NOW)).isEmpty();
    }

    @Test
    void revokeAllRevokesOnlyThatUser() {
        UserId u1 = user("a@mail.com");
        UserId u2 = user("b@mail.com");
        tokens.save(u1, "a".repeat(64), NOW.plusSeconds(60));
        tokens.save(u2, "b".repeat(64), NOW.plusSeconds(60));

        tokens.revokeAll(u1, NOW);

        assertThat(tokens.lockActive("a".repeat(64), NOW)).isEmpty();
        assertThat(tokens.lockActive("b".repeat(64), NOW)).isPresent();
    }

    @Test
    void sessionsRevokeAllUsesClock() {
        UserId u = user("a@mail.com");
        tokens.save(u, "a".repeat(64), Instant.now().plusSeconds(600));
        tokens.revokeAll(u);
        assertThat(tokens.lockActive("a".repeat(64), Instant.now())).isEmpty();
    }

    @Test
    void concurrentLockSerializes() throws Exception {
        UserId u = user("a@mail.com");
        String hash = "a".repeat(64);
        tokens.save(u, hash, NOW.plusSeconds(60));
        TransactionTemplate tx = new TransactionTemplate(tm);
        CountDownLatch firstHoldsLock = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Optional<?>> first = pool.submit(() -> tx.execute(s -> {
                Optional<?> found = tokens.lockActive(hash, NOW);
                firstHoldsLock.countDown();
                await(secondStarted);
                sleep(500); // let the second block on the row lock
                tokens.revoke(tokens.lockActive(hash, NOW).orElseThrow().id(), NOW);
                return found;
            }));
            firstHoldsLock.await(10, TimeUnit.SECONDS);
            Future<Optional<?>> second = pool.submit(() -> tx.execute(s -> {
                secondStarted.countDown();
                return tokens.lockActive(hash, NOW);
            }));
            long present = java.util.stream.Stream.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))
                    .filter(Optional::isPresent).count();
            assertThat(present).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
