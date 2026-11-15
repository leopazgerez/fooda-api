package com.fooda.api.user.infrastructure.in.web;

import com.fooda.api.user.domain.Role;

public record UpdateUserRequest(String fullName, Role role, Boolean active) {
}
