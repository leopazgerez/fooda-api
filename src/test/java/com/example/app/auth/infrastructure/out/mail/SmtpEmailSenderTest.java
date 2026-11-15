package com.example.app.auth.infrastructure.out.mail;

import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class SmtpEmailSenderTest {

    @Test
    void sendFailureDoesNotPropagate() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));
        assertThatCode(() -> new SmtpEmailSender(mailSender, "no-reply@x.com").send("a@b.com", "Hola", "cuerpo"))
                .doesNotThrowAnyException();
    }
}
