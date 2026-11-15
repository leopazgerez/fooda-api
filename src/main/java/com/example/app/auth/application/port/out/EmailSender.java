package com.example.app.auth.application.port.out;

public interface EmailSender {

    void send(String to, String subject, String body);
}
