package com.fooda.api.auth.infrastructure.in.web;

public record ResetPasswordRequest(String token, String newPassword) {
}
