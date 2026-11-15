package com.example.app.user.domain;

import com.example.app.shared.domain.ValidationException;

import java.util.Locale;
import java.util.Objects;

public record User(UserId id, String email, String fullName, String passwordHash, Role role,
                   boolean emailVerified, boolean active, Long version) {

    /** A user not loaded from storage yet (no version). */
    public User(UserId id, String email, String fullName, String passwordHash, Role role,
                boolean emailVerified, boolean active) {
        this(id, email, fullName, passwordHash, role, emailVerified, active, null);
    }

    public User {
        email = normalizeEmail(email);
        if (email.isEmpty()) {
            throw new ValidationException("email", "El email es obligatorio");
        }
        if (email.length() > 120) {
            throw new ValidationException("email", "El email puede tener como máximo 120 caracteres");
        }
        if (!email.contains("@")) {
            throw new ValidationException("email", "El email no es válido");
        }
        fullName = fullName == null ? "" : fullName.strip();
        if (fullName.isEmpty()) {
            throw new ValidationException("fullName", "El nombre es obligatorio");
        }
        if (fullName.length() > 120) {
            throw new ValidationException("fullName", "El nombre puede tener como máximo 120 caracteres");
        }
        Objects.requireNonNull(passwordHash, "passwordHash");
        if (role == null) {
            throw new ValidationException("role", "El rol es obligatorio");
        }
    }

    public static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    public User withFullName(String name) {
        return new User(id, email, name, passwordHash, role, emailVerified, active, version);
    }

    public User withPasswordHash(String hash) {
        return new User(id, email, fullName, hash, role, emailVerified, active, version);
    }

    public User withRole(Role newRole) {
        return new User(id, email, fullName, passwordHash, newRole, emailVerified, active, version);
    }

    public User withActive(boolean newActive) {
        return new User(id, email, fullName, passwordHash, role, emailVerified, newActive, version);
    }

    public User verified() {
        return new User(id, email, fullName, passwordHash, role, true, active, version);
    }

    @Override
    public String toString() {
        return "User[id=" + id + ", email=" + email + ", role=" + role + ", active=" + active + "]";
    }
}
