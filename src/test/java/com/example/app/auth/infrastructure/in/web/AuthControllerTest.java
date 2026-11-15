package com.example.app.auth.infrastructure.in.web;

import com.example.app.auth.application.exception.EmailNotVerifiedException;
import com.example.app.auth.application.exception.InvalidCredentialsException;
import com.example.app.auth.application.port.in.AuthUseCase;
import com.example.app.auth.application.port.in.Session;
import com.example.app.config.SecurityConfig;
import com.example.app.config.TimeConfig;
import com.example.app.shared.infrastructure.in.web.GlobalExceptionHandler;
import com.example.app.user.domain.Role;
import com.example.app.user.domain.User;
import com.example.app.user.domain.UserId;
import com.example.app.user.application.port.out.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({AuthController.class, PasswordController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, AuthExceptionHandler.class, TimeConfig.class})
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AuthUseCase auth;
    /** SecurityConfig needs it; jwt() bypasses the converter that uses it. */
    @MockitoBean UserRepository users;

    private static Session session() {
        User u = new User(new UserId(7), "a@b.com", "Ana", "H", Role.USER, true, true);
        return new Session("acc", Instant.parse("2026-01-01T00:00:00Z"), "ref", u);
    }

    @Test
    void registerIsPublicAnd201() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"password1\",\"fullName\":\"Ana\"}"))
                .andExpect(status().isCreated());
        verify(auth).register("a@b.com", "password1", "Ana");
    }

    @Test
    void loginReturnsSessionJson() throws Exception {
        given(auth.login("a@b.com", "pw")).willReturn(session());
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"pw\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("acc"))
                .andExpect(jsonPath("$.refreshToken").value("ref"))
                .andExpect(jsonPath("$.user.role").value("USER"));
    }

    @Test
    void invalidCredentialsIs401Json() throws Exception {
        given(auth.login("a@b.com", "bad")).willThrow(new InvalidCredentialsException());
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"bad\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void unverifiedIs403Json() throws Exception {
        given(auth.login("a@b.com", "pw")).willThrow(new EmailNotVerifiedException());
        mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\",\"password\":\"pw\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void forgotPasswordIsPublic204() throws Exception {
        mvc.perform(post("/api/auth/forgot-password").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\"}"))
                .andExpect(status().isNoContent());
        verify(auth).forgotPassword("a@b.com");
    }

    @Test
    void logoutNeedsToken() throws Exception {
        String body = "{\"refreshToken\":\"r\"}";
        mvc.perform(post("/api/auth/logout").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").with(jwt().jwt(j -> j.subject("7").claim("role", "USER")))
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
        verify(auth).logout("r");
    }

    @Test
    void changePasswordUsesSubject() throws Exception {
        given(auth.changePassword(new UserId(7), "old", "newpassword")).willReturn(session());
        mvc.perform(put("/api/me/password").with(jwt().jwt(j -> j.subject("7").claim("role", "USER")))
                        .contentType(APPLICATION_JSON)
                        .content("{\"currentPassword\":\"old\",\"newPassword\":\"newpassword\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("acc"));
    }
}
