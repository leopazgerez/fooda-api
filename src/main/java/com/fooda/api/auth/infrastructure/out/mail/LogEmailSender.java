package com.fooda.api.auth.infrastructure.out.mail;

import com.fooda.api.auth.application.port.out.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Development sender: writes the email to the log. Not a bean; wired in configuration. */
public class LogEmailSender implements EmailSender {

    private static final Logger LOG = LoggerFactory.getLogger(LogEmailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        LOG.info("Email a {}: {}\n{}", to, subject, body);
    }
}
