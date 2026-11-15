package com.fooda.api.auth.config;

import com.fooda.api.auth.application.port.in.AuthSettings;
import com.fooda.api.auth.application.port.out.AccessTokenIssuer;
import com.fooda.api.auth.application.port.out.EmailSender;
import com.fooda.api.auth.application.port.out.RefreshTokens;
import com.fooda.api.auth.application.port.out.UserTokens;
import com.fooda.api.auth.application.service.AuthService;
import com.fooda.api.auth.infrastructure.out.mail.LogEmailSender;
import com.fooda.api.auth.infrastructure.out.mail.SmtpEmailSender;
import com.fooda.api.shared.application.port.out.Transactions;
import com.fooda.api.user.application.port.out.PasswordHasher;
import com.fooda.api.user.application.port.out.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executor;

/** Factory for the auth module. AuthService is the single bean behind AuthUseCase. */
@Configuration(proxyBeanMethods = false)
public class AuthConfig {

    private static final Logger LOG = LoggerFactory.getLogger(AuthConfig.class);

    @Bean
    AuthService authService(UserRepository users, PasswordHasher hasher, AccessTokenIssuer access,
                            RefreshTokens refresh, UserTokens userTokens, EmailSender mail, Transactions tx,
                            Clock clock, @Value("${app.jwt.refresh-ttl}") Duration refreshTtl,
                            @Value("${app.frontend-url}") String frontendUrl) {
        return new AuthService(users, hasher, access, refresh, userTokens, mail, tx, clock,
                new AuthSettings(refreshTtl, frontendUrl));
    }

    @Bean
    EmailSender emailSender(@Value("${app.mail.mode}") String mode, @Value("${app.mail.from}") String from,
                            ObjectProvider<JavaMailSender> mailSender,
                            @Qualifier("applicationTaskExecutor") Executor executor) {
        return switch (mode) {
            case "log" -> {
                LOG.warn("APP_MAIL_MODE=log: emails are NOT sent, their links (tokens included) go to this log. Never use it in production.");
                yield new LogEmailSender();
            }
            case "smtp" -> {
                SmtpEmailSender smtp = new SmtpEmailSender(mailSender.getIfAvailable(() -> {
                    throw new IllegalStateException("APP_MAIL_MODE=smtp requiere SPRING_MAIL_HOST");
                }), from);
                // off the request thread: response time must not depend on SMTP (it would reveal which accounts exist)
                yield (to, subject, body) -> executor.execute(() -> smtp.send(to, subject, body));
            }
            default -> throw new IllegalStateException(
                    "APP_MAIL_MODE tiene que ser log (desarrollo) o smtp, no '" + mode + "'");
        };
    }
}
