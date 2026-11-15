package com.example.app.auth.infrastructure.in.web;

import com.example.app.auth.application.port.in.AuthUseCase;
import com.example.app.shared.infrastructure.in.web.Principals;
import com.example.app.user.domain.UserId;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/password")
public class PasswordController {

    private final AuthUseCase auth;

    public PasswordController(AuthUseCase auth) {
        this.auth = auth;
    }

    /** Returns the new session: the other sessions are revoked, so the caller keeps its login. */
    @PutMapping
    public SessionResponse change(@AuthenticationPrincipal Jwt jwt, @RequestBody ChangePasswordRequest b) {
        return SessionResponse.from(
                auth.changePassword(new UserId(Principals.userId(jwt)), b.currentPassword(), b.newPassword()));
    }
}
