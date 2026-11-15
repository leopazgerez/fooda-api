package com.example.app.auth.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthConfigTest {

    private final AuthConfig config = new AuthConfig();
    private final StaticListableBeanFactory noMailBeans = new StaticListableBeanFactory();

    @Test
    void smtpWithoutHostFailsFast() {
        assertThatThrownBy(() -> config.emailSender("smtp", "a@b.com", noMailBeans.getBeanProvider(
                org.springframework.mail.javamail.JavaMailSender.class), Runnable::run))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SPRING_MAIL_HOST");
    }

    @Test
    void unknownModeFailsFast() {
        assertThatThrownBy(() -> config.emailSender("pigeon", "a@b.com", noMailBeans.getBeanProvider(
                org.springframework.mail.javamail.JavaMailSender.class), Runnable::run))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("pigeon");
    }

    @Test
    void logModeNeedsNoMailSender() {
        assertThat(config.emailSender("log", "a@b.com", noMailBeans.getBeanProvider(
                org.springframework.mail.javamail.JavaMailSender.class), Runnable::run)).isNotNull();
    }
}
