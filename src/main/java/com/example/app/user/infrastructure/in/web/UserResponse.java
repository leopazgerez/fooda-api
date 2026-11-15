package com.example.app.user.infrastructure.in.web;

import com.example.app.user.domain.Permission;
import com.example.app.user.domain.Role;
import com.example.app.user.domain.User;

import java.util.List;

public record UserResponse(long id, String email, String fullName, Role role,
                           List<Permission> permissions, boolean emailVerified, boolean active) {

    public static UserResponse from(User u) {
        return new UserResponse(u.id().value(), u.email(), u.fullName(), u.role(),
                u.role().permissions().stream().sorted().toList(), u.emailVerified(), u.active());
    }
}
