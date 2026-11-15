package com.example.app.user.infrastructure.out.security;

import com.example.app.user.application.port.out.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    /** Compared against when the hash is missing, so a miss costs the same time as a wrong password. */
    private final String timingHash = encoder.encode("timing-equaliser");

    /** bcrypt only reads the first 72 bytes and the library throws beyond that; Password.check reports it to users. */
    private static final int MAX_BYTES = 72;

    private static boolean tooLong(String raw) {
        return raw.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
    }

    @Override
    public String hash(String raw) {
        if (tooLong(raw)) {
            throw new IllegalArgumentException("password longer than " + MAX_BYTES + " bytes");
        }
        return encoder.encode(raw);
    }

    @Override
    public boolean matches(String raw, String hash) {
        if (hash == null || tooLong(raw)) {
            encoder.matches("timing-equaliser", timingHash);
            return false;
        }
        return encoder.matches(raw, hash);
    }
}
