package com.fooda.api.auth.infrastructure.out.security;

import com.fooda.api.auth.application.port.out.IssuedToken;
import com.fooda.api.user.domain.Role;
import com.fooda.api.user.domain.User;
import com.fooda.api.user.domain.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import javax.crypto.spec.SecretKeySpec;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class JwtAccessTokenIssuerTest {

    @Test
    void issuesDecodableTokenWithClaimsAndTtl() {
        var key = new SecretKeySpec("0123456789abcdef0123456789abcdef".getBytes(), "HmacSHA256");
        JwtEncoder encoder = NimbusJwtEncoder.withSecretKey(key).build();
        JwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);

        IssuedToken token = new JwtAccessTokenIssuer(encoder, clock, Duration.ofMinutes(15))
                .issue(new User(new UserId(7), "a@mail.com", "Ana", "h", Role.ADMIN, true, true));

        Jwt jwt = decoder.decode(token.value());
        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(jwt.<String>getClaim("role")).isEqualTo("ADMIN");
        assertThat(jwt.getExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(15)));
        assertThat(token.expiresAt()).isEqualTo(now.plus(Duration.ofMinutes(15)));
    }
}
