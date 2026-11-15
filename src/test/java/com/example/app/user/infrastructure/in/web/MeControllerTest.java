package com.example.app.user.infrastructure.in.web;

import com.example.app.config.SecurityConfig;
import com.example.app.config.TimeConfig;
import com.example.app.shared.infrastructure.in.web.GlobalExceptionHandler;
import com.example.app.user.application.port.in.ProfileUseCase;
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

import static org.mockito.BDDMockito.given;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;

@WebMvcTest(MeController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, TimeConfig.class})
class MeControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ProfileUseCase profile;
    /** SecurityConfig needs it; jwt() bypasses the converter that uses it. */
    @MockitoBean UserRepository users;

    private static User user(String name) {
        return new User(new UserId(7), "a@b.com", name, "SECRET-HASH", Role.USER, true, true);
    }

    @Test
    void getMeUsesTokenSubject() throws Exception {
        given(profile.get(new UserId(7))).willReturn(user("Ana"));
        mvc.perform(get("/api/me").with(jwt().jwt(j -> j.subject("7").claim("role", "USER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("a@b.com"))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.permissions").isEmpty())
                .andExpect(content().string(not(containsString("SECRET-HASH"))))
                .andExpect(content().string(not(containsString("passwordHash"))));
    }

    @Test
    void adminSeesItsPermissions() throws Exception {
        given(profile.get(new UserId(1))).willReturn(
                new User(new UserId(1), "a@b.com", "Admin", "H", Role.ADMIN, true, true));
        mvc.perform(get("/api/me").with(jwt().jwt(j -> j.subject("1"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions[0]").value("USERS_READ"))
                .andExpect(jsonPath("$.permissions[1]").value("USERS_WRITE"));
    }

    @Test
    void patchRenames() throws Exception {
        given(profile.rename(new UserId(7), "Nueva")).willReturn(user("Nueva"));
        mvc.perform(patch("/api/me").with(jwt().jwt(j -> j.subject("7").claim("role", "USER")))
                        .contentType(APPLICATION_JSON).content("{\"fullName\":\"Nueva\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Nueva"));
    }
}
