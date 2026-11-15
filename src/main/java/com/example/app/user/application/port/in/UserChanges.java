package com.example.app.user.application.port.in;

import com.example.app.user.domain.Role;

/** A null field is left unchanged. */
public record UserChanges(String fullName, Role role, Boolean active) {
}
