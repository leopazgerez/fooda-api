package com.example.app.config;

import com.example.app.shared.infrastructure.in.web.ErrorResponse;
import com.example.app.shared.infrastructure.in.web.Principals;
import com.example.app.user.application.port.out.UserRepository;
import com.example.app.user.domain.Permission;
import com.example.app.user.domain.Role;
import com.example.app.user.domain.User;
import com.example.app.user.domain.UserId;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import tools.jackson.databind.json.JsonMapper;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.stream.Stream;

/** Stateless Bearer-JWT (HS256) security for /api/**; tokens come from POST /api/auth/login. */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, JwtDecoder jwtDecoder, JsonMapper json, Clock clock,
                                    UserRepository users)
            throws Exception {
        // No WWW-Authenticate header: a browser must never show its own login dialog
        AuthenticationEntryPoint unauthorized = (request, response, e) ->
                write(response, json, ErrorResponse.of(HttpStatus.UNAUTHORIZED, "Sesión vencida o no iniciada", clock));
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/verify-email",
                                "/api/auth/resend-verification", "/api/auth/login", "/api/auth/refresh",
                                "/api/auth/forgot-password", "/api/auth/reset-password").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.decoder(jwtDecoder).jwtAuthenticationConverter(currentUser(users)))
                        .authenticationEntryPoint(unauthorized))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler((request, response, e) ->
                                write(response, json, ErrorResponse.of(HttpStatus.FORBIDDEN, "No hay permiso para esta acción", clock))));
        return http.build();
    }

    /**
     * Role and active flag come from the database on every request, not from the token: a deactivated or demoted
     * user loses access immediately instead of when the access token expires.
     */
    // ponytail: one primary-key lookup per authenticated request; cache it briefly if that ever shows up in profiles
    private static Converter<Jwt, AbstractAuthenticationToken> currentUser(UserRepository users) {
        return jwt -> users.findById(new UserId(Principals.userId(jwt))).filter(User::active)
                .map(u -> new JwtAuthenticationToken(jwt, authorities(u.role()), jwt.getSubject()))
                .orElseThrow(() -> new InvalidBearerTokenException("User is inactive or no longer exists"));
    }

    private static void write(HttpServletResponse response, JsonMapper json, ErrorResponse error) throws IOException {
        response.setStatus(error.status());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        json.writeValue(response.getOutputStream(), error);
    }

    /** One authority per permission of the role, plus ROLE_<role>. */
    private static List<GrantedAuthority> authorities(Role role) {
        return Stream.concat(Stream.of("ROLE_" + role.name()), role.permissions().stream().map(Permission::name))
                .<GrantedAuthority>map(SimpleGrantedAuthority::new).toList();
    }

    @Bean
    SecretKey jwtSigningKey(@Value("${app.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) throw new IllegalStateException("APP_JWT_SECRET must be at least 32 bytes");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSigningKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSigningKey).build();
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtSigningKey) {
        return NimbusJwtDecoder.withSecretKey(jwtSigningKey).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(allowedOrigins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setExposedHeaders(List.of("Location"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }
}
