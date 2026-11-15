package com.fooda.api.user.application.port.in;

import com.fooda.api.user.domain.Role;

public record NewUser(String email, String fullName, Role role, String password) {
}
