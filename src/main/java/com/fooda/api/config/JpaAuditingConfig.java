package com.fooda.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.util.Optional;

@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(auditorAwareRef = "currentUserAuditor", dateTimeProviderRef = "auditClock")
public class JpaAuditingConfig {

    /** The JWT subject is the numeric user id. */
    @Bean
    AuditorAware<Long> currentUserAuditor() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(auth.getName()));
        };
    }

    @Bean
    DateTimeProvider auditClock(Clock clock) {
        return () -> Optional.of(clock.instant());
    }
}
