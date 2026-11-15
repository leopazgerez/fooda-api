package com.fooda.api.auth.infrastructure.out.security;

import com.fooda.api.auth.application.port.out.AccessTokenIssuer;
import com.fooda.api.auth.application.port.out.IssuedToken;
import com.fooda.api.user.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class JwtAccessTokenIssuer implements AccessTokenIssuer {

    private final JwtEncoder encoder;
    private final Clock clock;
    private final Duration ttl;

    public JwtAccessTokenIssuer(JwtEncoder encoder, Clock clock, @Value("${app.jwt.access-ttl}") Duration ttl) {
        this.encoder = encoder;
        this.clock = clock;
        this.ttl = ttl;
    }

    @Override
    public IssuedToken issue(User user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("app")
                .subject(String.valueOf(user.id().value()))
                .issuedAt(now)
                .expiresAt(expiresAt)
                .claim("email", user.email())
                .claim("name", user.fullName())
                .claim("role", user.role().name())
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return new IssuedToken(token, expiresAt);
    }
}
