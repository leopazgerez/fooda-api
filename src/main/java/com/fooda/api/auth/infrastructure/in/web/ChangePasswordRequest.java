package com.fooda.api.auth.infrastructure.in.web;

public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
