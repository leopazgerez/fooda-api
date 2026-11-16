package com.fooda.api.user.config;

import com.fooda.api.shared.application.port.out.Transactions;
import com.fooda.api.user.application.port.in.ManageUsersUseCase;
import com.fooda.api.user.application.port.out.PasswordHasher;
import com.fooda.api.user.application.port.out.UserRepository;
import com.fooda.api.user.application.port.out.UserSessions;
import com.fooda.api.user.application.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;

/** Factory for the user module. UserService is the single bean behind ProfileUseCase and ManageUsersUseCase. */
@Configuration(proxyBeanMethods = false)
public class UserConfig {

    private static final Logger LOG = LoggerFactory.getLogger(UserConfig.class);

    @Bean
    UserService userService(UserRepository users, PasswordHasher hasher, UserSessions sessions, Transactions tx) {
        return new UserService(users, hasher, sessions, tx);
    }

    @Bean
    @Order(0)
    ApplicationRunner adminBootstrap(ManageUsersUseCase manage,
                                     @Value("${app.admin.email:}") String email,
                                     @Value("${app.admin.password:}") String password,
                                     @Value("${app.admin.name:}") String name) {
        return args -> {
            if (email.isBlank() || password.isBlank()) {
                LOG.info("APP_ADMIN_EMAIL / APP_ADMIN_PASSWORD not set: skipping admin bootstrap");
                return;
            }
            try {
                if (manage.ensureAdmin(email, password, name.isBlank() ? email : name)) {
                    LOG.info("Created the initial admin user {}", email);
                }
            } catch (DataIntegrityViolationException e) {
                LOG.info("Initial admin {} was created by another instance", email);
            }
        };
    }
}
