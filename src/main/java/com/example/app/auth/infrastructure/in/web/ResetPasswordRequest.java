package com.example.app.auth.infrastructure.in.web;

public record ResetPasswordRequest(String token, String newPassword) {
}
