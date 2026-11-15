package com.fooda.api.auth.application.service;

import com.fooda.api.auth.application.exception.EmailNotVerifiedException;
import com.fooda.api.auth.application.exception.InvalidCredentialsException;
import com.fooda.api.auth.application.exception.InvalidLinkException;
import com.fooda.api.auth.application.port.in.AuthSettings;
import com.fooda.api.auth.application.port.in.Session;
import com.fooda.api.auth.domain.SecureTokens;
import com.fooda.api.shared.domain.ValidationException;
import com.fooda.api.user.application.service.InMemoryUserRepository;
import com.fooda.api.user.application.service.PlainHasher;
import com.fooda.api.user.domain.Role;
import com.fooda.api.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthServiceTest {

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2030-01-01T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    MutableClock clock;
    InMemoryUserRepository users;
    InMemoryRefreshTokens refresh;
    RecordingEmailSender mail;
    AuthService auth;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        users = new InMemoryUserRepository();
        refresh = new InMemoryRefreshTokens();
        mail = new RecordingEmailSender();
        auth = new AuthService(users, new PlainHasher(), new FakeAccessTokenIssuer(), refresh,
                new InMemoryUserTokens(), mail, new RollbackOnThrowTransactions(refresh), clock,
                new AuthSettings(Duration.ofDays(30), "http://front"));
    }

    void registerAndVerify() {
        auth.register("ana@mail.com", "secret123", "Ana");
        auth.verifyEmail(mail.lastToken());
    }

    @Test
    void registerCreatesUnverifiedUserAndSendsLink() {
        auth.register("ana@mail.com", "secret123", "Ana");
        User u = users.findByEmail("ana@mail.com").orElseThrow();
        assertThat(u.role()).isEqualTo(Role.USER);
        assertThat(u.emailVerified()).isFalse();
        assertThat(mail.sent).hasSize(1);
        assertThat(mail.sent.get(0).to()).isEqualTo("ana@mail.com");
        assertThat(mail.sent.get(0).body()).contains("http://front/verify-email?token=");
    }

    @Test
    void registerExistingEmailDoesNotRevealIt() {
        registerAndVerify();
        auth.register("ANA@mail.com", "other-pass1", "Otra");
        assertThat(mail.sent.get(mail.sent.size() - 1).subject()).isEqualTo("Ya tenés una cuenta");
        assertThat(users.findByEmail("ana@mail.com").orElseThrow().fullName()).isEqualTo("Ana");
    }

    @Test
    void registerUnverifiedEmailAgainResendsVerification() {
        auth.register("ana@mail.com", "secret123", "Ana");
        String first = mail.lastToken();
        auth.register("ana@mail.com", "secret123", "Ana");
        auth.verifyEmail(mail.lastToken());
        assertThatThrownBy(() -> auth.verifyEmail(first)).isInstanceOf(InvalidLinkException.class);
    }

    @Test
    void changePasswordKillsPendingResetLink() {
        registerAndVerify();
        auth.forgotPassword("ana@mail.com");
        String reset = mail.lastToken();
        auth.changePassword(users.findByEmail("ana@mail.com").orElseThrow().id(), "secret123", "newsecret1");
        assertThatThrownBy(() -> auth.resetPassword(reset, "attacker1")).isInstanceOf(InvalidLinkException.class);
    }

    @Test
    void registerChecksPassword() {
        assertThatThrownBy(() -> auth.register("ana@mail.com", "short", "Ana"))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.field()).isEqualTo("password"));
    }

    @Test
    void verifyEmailMarksVerified() {
        registerAndVerify();
        assertThat(users.findByEmail("ana@mail.com").orElseThrow().emailVerified()).isTrue();
    }

    @Test
    void verifyEmailTwiceIsInvalidLink() {
        registerAndVerify();
        String token = mail.lastToken();
        assertThatThrownBy(() -> auth.verifyEmail(token)).isInstanceOf(InvalidLinkException.class);
    }

    @Test
    void verifyEmailAfter24hIsInvalidLink() {
        auth.register("ana@mail.com", "secret123", "Ana");
        clock.now = clock.now.plus(Duration.ofHours(24)).plusSeconds(1);
        String token = mail.lastToken();
        assertThatThrownBy(() -> auth.verifyEmail(token)).isInstanceOf(InvalidLinkException.class);
    }

    @Test
    void resendInvalidatesPreviousLink() {
        auth.register("ana@mail.com", "secret123", "Ana");
        String first = mail.lastToken();
        auth.resendVerification("ana@mail.com");
        String second = mail.lastToken();
        assertThatThrownBy(() -> auth.verifyEmail(first)).isInstanceOf(InvalidLinkException.class);
        auth.verifyEmail(second);
        assertThat(users.findByEmail("ana@mail.com").orElseThrow().emailVerified()).isTrue();
    }

    @Test
    void resendForUnknownOrVerifiedSendsNothing() {
        auth.resendVerification("nadie@mail.com");
        assertThat(mail.sent).isEmpty();
        registerAndVerify();
        int before = mail.sent.size();
        auth.resendVerification("ana@mail.com");
        assertThat(mail.sent).hasSize(before);
    }

    @Test
    void loginReturnsSessionAndStoresHashedRefresh() {
        registerAndVerify();
        Session s = auth.login("ana@mail.com", "secret123");
        assertThat(s.accessToken()).isEqualTo("access-ana@mail.com");
        assertThat(s.user().email()).isEqualTo("ana@mail.com");
        var row = refresh.rows.get(refresh.rows.size() - 1);
        assertThat(row.hash).isEqualTo(SecureTokens.hash(s.refreshToken())).isNotEqualTo(s.refreshToken());
        assertThat(row.expiresAt).isEqualTo(clock.now.plus(Duration.ofDays(30)));
    }

    @Test
    void loginWithMixedCaseEmailWorks() {
        registerAndVerify();
        assertThat(auth.login(" ANA@mail.com ", "secret123").user().email()).isEqualTo("ana@mail.com");
    }

    @Test
    void loginWrongPasswordOrUnknownIs401() {
        registerAndVerify();
        assertThatThrownBy(() -> auth.login("ana@mail.com", "wrong-pass")).isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Email o contraseña incorrectos");
        assertThatThrownBy(() -> auth.login("nadie@mail.com", "secret123")).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginInactiveIs401() {
        registerAndVerify();
        users.save(users.findByEmail("ana@mail.com").orElseThrow().withActive(false));
        assertThatThrownBy(() -> auth.login("ana@mail.com", "secret123")).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginUnverifiedIs403() {
        auth.register("ana@mail.com", "secret123", "Ana");
        assertThatThrownBy(() -> auth.login("ana@mail.com", "secret123")).isInstanceOf(EmailNotVerifiedException.class);
        assertThatThrownBy(() -> auth.login("ana@mail.com", "wrong-pass")).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void refreshRotates() {
        registerAndVerify();
        Session first = auth.login("ana@mail.com", "secret123");
        Session second = auth.refresh(first.refreshToken());
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
        assertThatThrownBy(() -> auth.refresh(first.refreshToken()))
                .isInstanceOf(InvalidCredentialsException.class).hasMessage("La sesión no es válida o expiró");
        assertThat(auth.refresh(second.refreshToken()).user().email()).isEqualTo("ana@mail.com");
    }

    @Test
    void refreshForDeactivatedUserIs401() {
        registerAndVerify();
        Session s = auth.login("ana@mail.com", "secret123");
        users.save(users.findByEmail("ana@mail.com").orElseThrow().withActive(false));
        assertThatThrownBy(() -> auth.refresh(s.refreshToken()))
                .isInstanceOf(InvalidCredentialsException.class).hasMessage("La sesión no es válida o expiró");
        assertThat(refresh.rows.get(0).revokedAt).isNotNull();
    }

    @Test
    void verifyEmailForInactiveUserIsInvalidLink() {
        auth.register("ana@mail.com", "secret123", "Ana");
        users.save(users.findByEmail("ana@mail.com").orElseThrow().withActive(false));
        String token = mail.lastToken();
        assertThatThrownBy(() -> auth.verifyEmail(token)).isInstanceOf(InvalidLinkException.class);
    }

    @Test
    void resetPasswordForInactiveUserIsInvalidLink() {
        registerAndVerify();
        auth.forgotPassword("ana@mail.com");
        users.save(users.findByEmail("ana@mail.com").orElseThrow().withActive(false));
        String token = mail.lastToken();
        assertThatThrownBy(() -> auth.resetPassword(token, "newsecret1")).isInstanceOf(InvalidLinkException.class);
    }

    @Test
    void logoutRevokesAndIsIdempotent() {
        registerAndVerify();
        Session s = auth.login("ana@mail.com", "secret123");
        auth.logout(s.refreshToken());
        auth.logout(s.refreshToken());
        assertThatCode(() -> auth.logout("desconocido")).doesNotThrowAnyException();
        assertThatThrownBy(() -> auth.refresh(s.refreshToken())).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void forgotPasswordUnknownOrInactiveSendsNothing() {
        registerAndVerify();
        int before = mail.sent.size();
        auth.forgotPassword("nadie@mail.com");
        users.save(users.findByEmail("ana@mail.com").orElseThrow().withActive(false));
        auth.forgotPassword("ana@mail.com");
        assertThat(mail.sent).hasSize(before);
    }

    @Test
    void forgotPasswordSendsResetLink() {
        registerAndVerify();
        auth.forgotPassword("ana@mail.com");
        assertThat(mail.sent.get(mail.sent.size() - 1).body()).contains("http://front/reset-password?token=");
        clock.now = clock.now.plus(Duration.ofMinutes(59));
        auth.resetPassword(mail.lastToken(), "newsecret1");
        assertThat(auth.login("ana@mail.com", "newsecret1")).isNotNull();
    }

    @Test
    void resetPasswordChangesHashVerifiesAndRevokesAll() {
        auth.register("ana@mail.com", "secret123", "Ana");
        auth.verifyEmail(mail.lastToken());
        Session old = auth.login("ana@mail.com", "secret123");
        auth.forgotPassword("ana@mail.com");
        auth.resetPassword(mail.lastToken(), "newsecret1");
        assertThat(auth.login("ana@mail.com", "newsecret1").user().emailVerified()).isTrue();
        assertThatThrownBy(() -> auth.login("ana@mail.com", "secret123")).isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> auth.refresh(old.refreshToken())).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void resetVerifiesAnUnverifiedUser() {
        auth.register("ana@mail.com", "secret123", "Ana");
        auth.forgotPassword("ana@mail.com");
        auth.resetPassword(mail.lastToken(), "newsecret1");
        assertThat(users.findByEmail("ana@mail.com").orElseThrow().emailVerified()).isTrue();
    }

    @Test
    void resetAfter1hIsInvalidLink() {
        registerAndVerify();
        auth.forgotPassword("ana@mail.com");
        clock.now = clock.now.plus(Duration.ofHours(1)).plusSeconds(1);
        String token = mail.lastToken();
        assertThatThrownBy(() -> auth.resetPassword(token, "newsecret1")).isInstanceOf(InvalidLinkException.class);
    }

    @Test
    void resetChecksNewPassword() {
        registerAndVerify();
        auth.forgotPassword("ana@mail.com");
        String token = mail.lastToken();
        assertThatThrownBy(() -> auth.resetPassword(token, "short"))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.field()).isEqualTo("newPassword"));
    }

    @Test
    void changePasswordWrongCurrentIs400() {
        registerAndVerify();
        var id = users.findByEmail("ana@mail.com").orElseThrow().id();
        assertThatThrownBy(() -> auth.changePassword(id, "wrong-pass", "newsecret1"))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.field()).isEqualTo("currentPassword");
                    assertThat(e.getMessage()).isEqualTo("La contraseña actual no es correcta");
                });
    }

    @Test
    void changePasswordRevokesOthersAndReturnsNewSession() {
        registerAndVerify();
        Session old = auth.login("ana@mail.com", "secret123");
        Session fresh = auth.changePassword(old.user().id(), "secret123", "newsecret1");
        assertThatThrownBy(() -> auth.refresh(old.refreshToken())).isInstanceOf(InvalidCredentialsException.class);
        assertThat(auth.refresh(fresh.refreshToken()).user().email()).isEqualTo("ana@mail.com");
        assertThat(auth.login("ana@mail.com", "newsecret1")).isNotNull();
    }
}
