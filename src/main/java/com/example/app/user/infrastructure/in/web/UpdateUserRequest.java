package com.example.app.user.infrastructure.in.web;

import com.example.app.user.domain.Role;

public record UpdateUserRequest(String fullName, Role role, Boolean active) {
}
