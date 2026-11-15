package com.example.app.auth.infrastructure.in.web;

public record RegisterRequest(String email, String password, String fullName) {
}
