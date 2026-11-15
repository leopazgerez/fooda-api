package com.example.app.support;

import com.example.app.auth.application.port.out.EmailSender;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Keeps the last email body per recipient so a test can read the link token. */
public class CapturingEmailSender implements EmailSender {

    private static final Pattern TOKEN = Pattern.compile("[?]token=(\\S+)");
    private final Map<String, String> last = new ConcurrentHashMap<>();

    @Override
    public void send(String to, String subject, String body) {
        last.put(to, body);
    }

    public boolean sentTo(String to) {
        return last.containsKey(to);
    }

    /** The raw token in the last link sent to {@code to}. */
    public String tokenFor(String to) {
        Matcher m = TOKEN.matcher(last.get(to));
        if (!m.find()) throw new IllegalStateException("No token in the email to " + to);
        return m.group(1);
    }
}
