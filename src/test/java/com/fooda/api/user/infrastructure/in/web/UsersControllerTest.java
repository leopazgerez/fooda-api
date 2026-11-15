package com.fooda.api.user.infrastructure.in.web;

import com.fooda.api.config.SecurityConfig;
import com.fooda.api.config.TimeConfig;
import com.fooda.api.shared.application.Page;
import com.fooda.api.shared.infrastructure.in.web.GlobalExceptionHandler;
import com.fooda.api.user.application.port.in.ManageUsersUseCase;
import com.fooda.api.user.application.port.in.NewUser;
import com.fooda.api.user.application.port.in.UserChanges;
import com.fooda.api.user.domain.Role;
import com.fooda.api.user.domain.User;
import com.fooda.api.user.domain.UserId;
import com.fooda.api.user.application.port.out.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UsersController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, TimeConfig.class})
class UsersControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ManageUsersUseCase manage;
    /** SecurityConfig needs it; jwt() bypasses the converter that uses it. */
    @MockitoBean UserRepository users;

    private static User user(long id) {
        return new User(new UserId(id), "u" + id + "@b.com", "User " + id, "h", Role.USER, true, true);
    }

    @Test
    void userRoleIs403() throws Exception {
        mvc.perform(get("/api/users").with(jwt().jwt(j -> j.subject("7").claim("role", "USER"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void readOnlyCannotCreate() throws Exception {
        mvc.perform(post("/api/users").with(jwt().authorities(new SimpleGrantedAuthority("USERS_READ")))
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"u9@b.com\",\"fullName\":\"User 9\",\"role\":\"USER\",\"password\":\"password123\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("No hay permiso para esta acción"));
    }

    @Test
    void adminListsPage() throws Exception {
        given(manage.list(1, 2)).willReturn(new Page<>(List.of(user(3), user(4)), 1, 2, 5));
        mvc.perform(get("/api/users?page=1&size=2").with(jwt().authorities(new SimpleGrantedAuthority("USERS_READ"), new SimpleGrantedAuthority("USERS_WRITE")).jwt(j -> j.subject("1").claim("role", "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].email").value("u3@b.com"))
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        given(manage.create(any(NewUser.class))).willReturn(user(9));
        mvc.perform(post("/api/users").with(jwt().authorities(new SimpleGrantedAuthority("USERS_READ"), new SimpleGrantedAuthority("USERS_WRITE")).jwt(j -> j.subject("1").claim("role", "ADMIN")))
                        .contentType(APPLICATION_JSON)
                        .content("{\"email\":\"u9@b.com\",\"fullName\":\"User 9\",\"role\":\"USER\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/users/9")));
    }

    @Test
    void patchPassesActorAndTarget() throws Exception {
        UserChanges changes = new UserChanges(null, Role.ADMIN, false);
        given(manage.update(new UserId(1), new UserId(5), changes)).willReturn(user(5));
        mvc.perform(patch("/api/users/5").with(jwt().authorities(new SimpleGrantedAuthority("USERS_READ"), new SimpleGrantedAuthority("USERS_WRITE")).jwt(j -> j.subject("1").claim("role", "ADMIN")))
                        .contentType(APPLICATION_JSON).content("{\"role\":\"ADMIN\",\"active\":false}"))
                .andExpect(status().isOk());
        verify(manage).update(new UserId(1), new UserId(5), changes);
    }
}
