package com.example.app.user.application.port.in;

import com.example.app.user.domain.Role;

public record NewUser(String email, String fullName, Role role, String password) {
}
