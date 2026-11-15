package com.example.app.user.application.port.out;

public interface PasswordHasher {

    String hash(String raw);

    /** False when {@code hashOrNull} is null. */
    boolean matches(String raw, String hashOrNull);
}
