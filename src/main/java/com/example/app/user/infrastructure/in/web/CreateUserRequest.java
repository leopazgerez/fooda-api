package com.example.app.user.infrastructure.in.web;

import com.example.app.user.domain.Role;

public record CreateUserRequest(String email, String fullName, Role role, String password) {
}
