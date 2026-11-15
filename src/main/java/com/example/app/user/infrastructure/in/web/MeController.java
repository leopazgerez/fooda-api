package com.example.app.user.infrastructure.in.web;

import com.example.app.user.application.port.in.ProfileUseCase;
import com.example.app.user.domain.UserId;
import com.example.app.shared.infrastructure.in.web.Principals;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final ProfileUseCase profile;

    public MeController(ProfileUseCase profile) {
        this.profile = profile;
    }

    @GetMapping
    public UserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return UserResponse.from(profile.get(new UserId(Principals.userId(jwt))));
    }

    @PatchMapping
    public UserResponse rename(@AuthenticationPrincipal Jwt jwt, @RequestBody RenameRequest body) {
        return UserResponse.from(profile.rename(new UserId(Principals.userId(jwt)), body.fullName()));
    }
}
