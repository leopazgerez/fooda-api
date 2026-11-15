package com.example.app.auth.application.service;

import com.example.app.auth.application.exception.EmailNotVerifiedException;
import com.example.app.auth.application.exception.InvalidCredentialsException;
import com.example.app.auth.application.exception.InvalidLinkException;
import com.example.app.auth.application.port.in.AuthSettings;
import com.example.app.auth.application.port.in.AuthUseCase;
import com.example.app.auth.application.port.in.Session;
import com.example.app.auth.application.port.out.AccessTokenIssuer;
import com.example.app.auth.application.port.out.EmailSender;
import com.example.app.auth.application.port.out.IssuedToken;
import com.example.app.auth.application.port.out.RefreshTokens;
import com.example.app.auth.application.port.out.UserTokens;
import com.example.app.auth.domain.SecureTokens;
import com.example.app.auth.domain.TokenType;
import com.example.app.shared.application.port.out.Transactions;
import com.example.app.shared.domain.ValidationException;
import com.example.app.user.application.port.out.PasswordHasher;
import com.example.app.user.application.port.out.UserRepository;
import com.example.app.user.domain.Password;
import com.example.app.user.domain.Role;
import com.example.app.user.domain.User;
import com.example.app.user.domain.UserId;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public class AuthService implements AuthUseCase {

    private static final Duration VERIFY_TTL = Duration.ofHours(24);
    private static final Duration RESET_TTL = Duration.ofHours(1);
    private static final String BAD_SESSION = "La sesión no es válida o expiró";

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final AccessTokenIssuer access;
    private final RefreshTokens refreshTokens;
    private final UserTokens userTokens;
    private final EmailSender email;
    private final Transactions tx;
    private final Clock clock;
    private final AuthSettings settings;

    public AuthService(UserRepository users, PasswordHasher hasher, AccessTokenIssuer access,
                       RefreshTokens refreshTokens, UserTokens userTokens, EmailSender email,
                       Transactions tx, Clock clock, AuthSettings settings) {
        this.users = users;
        this.hasher = hasher;
        this.access = access;
        this.refreshTokens = refreshTokens;
        this.userTokens = userTokens;
        this.email = email;
        this.tx = tx;
        this.clock = clock;
        this.settings = settings;
    }

    @Override
    public void register(String emailAddress, String password, String fullName) {
        Password.check("password", password);
        // validated and hashed up front, so a new and an existing email cost the same
        User candidate = new User(null, emailAddress, fullName, hasher.hash(password), Role.USER, false, true);
        // an existing email gets an email instead of a 409, so the response never reveals which accounts exist
        send(tx.get(() -> Optional.of(users.findByEmail(candidate.email())
                .map(u -> u.active() && !u.emailVerified() ? verificationMail(u) : alreadyRegisteredMail(u))
                .orElseGet(() -> verificationMail(users.save(candidate))))));
    }

    @Override
    public void verifyEmail(String token) {
        tx.run(() -> {
            UserId id = userTokens.consume(TokenType.VERIFY_EMAIL, SecureTokens.hash(String.valueOf(token)), clock.instant())
                    .orElseThrow(InvalidLinkException::new);
            User user = users.findById(id).filter(User::active).orElseThrow(InvalidLinkException::new);
            users.save(user.verified());
        });
    }

    @Override
    public void resendVerification(String emailAddress) {
        send(tx.get(() -> users.findByEmail(User.normalizeEmail(emailAddress))
                .filter(u -> u.active() && !u.emailVerified())
                .map(this::verificationMail)));
    }

    @Override
    public Session login(String emailAddress, String password) {
        Optional<User> user = users.findByEmail(User.normalizeEmail(emailAddress));
        // always hash-compare, even for an unknown email, so timing does not reveal accounts
        boolean matches = hasher.matches(password == null ? "" : password, user.map(User::passwordHash).orElse(null));
        if (!matches || !user.get().active()) throw new InvalidCredentialsException();
        if (!user.get().emailVerified()) throw new EmailNotVerifiedException();
        return tx.get(() -> issueSession(user.get()));
    }

    @Override
    public Session refresh(String refreshToken) {
        // the revoke must commit even when the user is gone, so the 401 is thrown after the tx
        Optional<Session> session = tx.get(() -> {
            Instant now = clock.instant();
            var stored = refreshTokens.lockActive(SecureTokens.hash(String.valueOf(refreshToken)), now);
            if (stored.isEmpty()) return Optional.empty();
            refreshTokens.revoke(stored.get().id(), now);
            return users.findById(stored.get().userId()).filter(User::active).map(this::issueSession);
        });
        return session.orElseThrow(() -> new InvalidCredentialsException(BAD_SESSION));
    }

    @Override
    public void logout(String refreshToken) {
        tx.run(() -> {
            Instant now = clock.instant();
            refreshTokens.lockActive(SecureTokens.hash(String.valueOf(refreshToken)), now)
                    .ifPresent(s -> refreshTokens.revoke(s.id(), now));
        });
    }

    @Override
    public void forgotPassword(String emailAddress) {
        send(tx.get(() -> users.findByEmail(User.normalizeEmail(emailAddress)).filter(User::active).map(user -> {
            userTokens.invalidateAll(user.id(), TokenType.RESET_PASSWORD, clock.instant());
            String raw = newUserToken(user, TokenType.RESET_PASSWORD, RESET_TTL);
            return new Mail(user.email(), "Restablecé tu contraseña",
                    "Hola " + user.fullName() + ",\n\nPara elegir una nueva contraseña entrá a este enlace (vence en 1 hora):\n"
                            + settings.frontendUrl() + "/reset-password?token=" + raw
                            + "\n\nSi no lo pediste, ignorá este mensaje.");
        })));
    }

    @Override
    public void resetPassword(String token, String newPassword) {
        tx.run(() -> {
            Instant now = clock.instant();
            UserId id = userTokens.consume(TokenType.RESET_PASSWORD, SecureTokens.hash(String.valueOf(token)), now)
                    .orElseThrow(InvalidLinkException::new);
            Password.check("newPassword", newPassword);
            User user = users.findById(id).filter(User::active).orElseThrow(InvalidLinkException::new);
            users.save(user.withPasswordHash(hasher.hash(newPassword)).verified());
            userTokens.invalidateAll(id, TokenType.RESET_PASSWORD, now);
            refreshTokens.revokeAll(id, now);
        });
    }

    @Override
    public Session changePassword(UserId userId, String currentPassword, String newPassword) {
        return tx.get(() -> {
            User user = users.findById(userId).filter(User::active).orElseThrow(InvalidCredentialsException::new);
            if (!hasher.matches(currentPassword == null ? "" : currentPassword, user.passwordHash())) {
                throw new ValidationException("currentPassword", "La contraseña actual no es correcta");
            }
            Password.check("newPassword", newPassword);
            User saved = users.save(user.withPasswordHash(hasher.hash(newPassword)));
            // an older reset link must not be able to overwrite the new password
            userTokens.invalidateAll(userId, TokenType.RESET_PASSWORD, clock.instant());
            refreshTokens.revokeAll(userId, clock.instant());
            return issueSession(saved);
        });
    }

    private record Mail(String to, String subject, String body) {
    }

    /** Called after the transaction committed, so a link is never sent for a token that was rolled back. */
    private void send(Optional<Mail> mail) {
        mail.ifPresent(m -> email.send(m.to(), m.subject(), m.body()));
    }

    private Mail verificationMail(User user) {
        userTokens.invalidateAll(user.id(), TokenType.VERIFY_EMAIL, clock.instant());
        String raw = newUserToken(user, TokenType.VERIFY_EMAIL, VERIFY_TTL);
        return new Mail(user.email(), "Verificá tu email",
                "Hola " + user.fullName() + ",\n\nPara activar tu cuenta entrá a este enlace (vence en 24 horas):\n"
                        + settings.frontendUrl() + "/verify-email?token=" + raw);
    }

    private Mail alreadyRegisteredMail(User user) {
        return new Mail(user.email(), "Ya tenés una cuenta",
                "Hola " + user.fullName() + ",\n\nAlguien intentó registrarse con este email, pero ya tenés una cuenta."
                        + " Si olvidaste tu contraseña, podés elegir una nueva en:\n"
                        + settings.frontendUrl() + "/forgot-password\n\nSi no fuiste vos, ignorá este mensaje.");
    }

    private String newUserToken(User user, TokenType type, Duration ttl) {
        String raw = SecureTokens.generate();
        userTokens.save(user.id(), type, SecureTokens.hash(raw), clock.instant().plus(ttl));
        return raw;
    }

    private Session issueSession(User user) {
        IssuedToken token = access.issue(user);
        String raw = SecureTokens.generate();
        refreshTokens.save(user.id(), SecureTokens.hash(raw), clock.instant().plus(settings.refreshTtl()));
        return new Session(token.value(), token.expiresAt(), raw, user);
    }
}
