package com.example.app.auth.infrastructure.in.web;

public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
