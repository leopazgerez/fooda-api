package com.fooda.api.auth.application.service;

import com.fooda.api.auth.application.port.out.EmailSender;

import java.util.ArrayList;
import java.util.List;

public class RecordingEmailSender implements EmailSender {

    public record Sent(String to, String subject, String body) {
    }

    public final List<Sent> sent = new ArrayList<>();

    @Override
    public void send(String to, String subject, String body) {
        sent.add(new Sent(to, subject, body));
    }

    /** The raw token in the link of the last email. */
    public String lastToken() {
        String body = sent.get(sent.size() - 1).body();
        return body.substring(body.indexOf("token=") + 6).strip().split("\\s")[0];
    }
}
