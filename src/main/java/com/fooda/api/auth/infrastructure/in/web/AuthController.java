package com.fooda.api.auth.infrastructure.in.web;

import com.fooda.api.auth.application.port.in.AuthUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthUseCase auth;

    public AuthController(AuthUseCase auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@RequestBody RegisterRequest b) {
        auth.register(b.email(), b.password(), b.fullName());
    }

    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void verifyEmail(@RequestBody TokenRequest b) {
        auth.verifyEmail(b.token());
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resendVerification(@RequestBody EmailRequest b) {
        auth.resendVerification(b.email());
    }

    @PostMapping("/login")
    public SessionResponse login(@RequestBody LoginRequest b) {
        return SessionResponse.from(auth.login(b.email(), b.password()));
    }

    @PostMapping("/refresh")
    public SessionResponse refresh(@RequestBody RefreshRequest b) {
        return SessionResponse.from(auth.refresh(b.refreshToken()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody RefreshRequest b) {
        auth.logout(b.refreshToken());
    }

    @PostMapping("/forgot-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void forgotPassword(@RequestBody EmailRequest b) {
        auth.forgotPassword(b.email());
    }

    @PostMapping("/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@RequestBody ResetPasswordRequest b) {
        auth.resetPassword(b.token(), b.newPassword());
    }
}
