package com.fooda.api.user.infrastructure.in.web;

import com.fooda.api.user.domain.Role;

public record CreateUserRequest(String email, String fullName, Role role, String password) {
}
