package com.fooda.api.auth.application.port.out;

public interface EmailSender {

    void send(String to, String subject, String body);
}
