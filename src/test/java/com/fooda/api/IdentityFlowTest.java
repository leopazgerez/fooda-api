package com.fooda.api;

import com.fooda.api.support.CapturingEmailSender;
import com.fooda.api.user.application.port.out.PasswordHasher;
import com.fooda.api.user.application.port.out.UserRepository;
import com.fooda.api.user.domain.Role;
import com.fooda.api.user.domain.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.admin.email=admin@test.com", "app.admin.password=admin-pass-123"})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, IdentityFlowTest.Mail.class})
class IdentityFlowTest {

    static final String ADMIN_EMAIL = "admin@test.com";
    static final String ADMIN_PASSWORD = "admin-pass-123";

    @TestConfiguration(proxyBeanMethods = false)
    static class Mail {
        @Bean
        @Primary
        CapturingEmailSender capturingEmailSender() {
            return new CapturingEmailSender();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired CapturingEmailSender mail;
    @Autowired UserRepository users;
    @Autowired PasswordHasher hasher;
    @Autowired JdbcTemplate jdbc;

    /** Other test classes expect an empty app_user. */
    @AfterEach
    void clean() {
        jdbc.update("DELETE FROM refresh_token");
        jdbc.update("DELETE FROM user_token");
        jdbc.update("TRUNCATE app_user CASCADE");
    }

    /** Other test classes wipe app_user, so the bootstrap admin may be missing: make sure it is there. */
    @BeforeEach
    void ensureAdmin() {
        String hash = hasher.hash(ADMIN_PASSWORD);
        User admin = users.findByEmail(ADMIN_EMAIL)
                .map(u -> u.withPasswordHash(hash).withRole(Role.ADMIN).withActive(true))
                .orElseGet(() -> new User(null, ADMIN_EMAIL, "Admin", hash, Role.ADMIN, true, true));
        users.save(admin);
    }

    private static String uniqueEmail() {
        return "u" + UUID.randomUUID().toString().substring(0, 8) + "@mail.com";
    }

    private ResultActions json(String path, String body) throws Exception {
        return mvc.perform(post(path).contentType(APPLICATION_JSON).content(body));
    }

    private ResultActions register(String email, String password) throws Exception {
        return json("/api/auth/register",
                "{\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"fullName\":\"Ana\"}");
    }

    private ResultActions login(String email, String password) throws Exception {
        return json("/api/auth/login", "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
    }

    private static String field(ResultActions r, String path) throws Exception {
        return JsonPath.read(r.andReturn().getResponse().getContentAsString(), path);
    }

    private void registerAndVerify(String email, String password) throws Exception {
        register(email, password).andExpect(status().isCreated());
        json("/api/auth/verify-email", "{\"token\":\"" + mail.tokenFor(email) + "\"}")
                .andExpect(status().isNoContent());
    }

    @Test
    void fullFlow() throws Exception {
        String email = uniqueEmail();
        register(" " + email.toUpperCase().replace("@MAIL.COM", "@Mail.com") + " ", "password1")
                .andExpect(status().isCreated());
        login(email, "password1").andExpect(status().isForbidden());

        json("/api/auth/verify-email", "{\"token\":\"" + mail.tokenFor(email) + "\"}")
                .andExpect(status().isNoContent());
        ResultActions ok = login(email, "password1").andExpect(status().isOk());
        String access = field(ok, "$.accessToken");
        String refresh = field(ok, "$.refreshToken");

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        ResultActions refreshed = json("/api/auth/refresh", "{\"refreshToken\":\"" + refresh + "\"}")
                .andExpect(status().isOk());
        String newRefresh = field(refreshed, "$.refreshToken");
        assertThat(newRefresh).isNotEqualTo(refresh);
        json("/api/auth/refresh", "{\"refreshToken\":\"" + refresh + "\"}").andExpect(status().isUnauthorized());

        // logout needs a Bearer token; the access token of the refreshed session is still valid
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + field(refreshed, "$.accessToken"))
                        .contentType(APPLICATION_JSON).content("{\"refreshToken\":\"" + newRefresh + "\"}"))
                .andExpect(status().isNoContent());
        json("/api/auth/refresh", "{\"refreshToken\":\"" + newRefresh + "\"}").andExpect(status().isUnauthorized());
    }

    @Test
    void passwordReset() throws Exception {
        String email = uniqueEmail();
        registerAndVerify(email, "old-password1");

        json("/api/auth/forgot-password", "{\"email\":\"" + email + "\"}").andExpect(status().isNoContent());
        json("/api/auth/reset-password",
                "{\"token\":\"" + mail.tokenFor(email) + "\",\"newPassword\":\"new-password1\"}")
                .andExpect(status().isNoContent());

        login(email, "new-password1").andExpect(status().isOk());
        login(email, "old-password1").andExpect(status().isUnauthorized());
    }

    @Test
    void adminDeactivatesUser() throws Exception {
        String email = uniqueEmail();
        registerAndVerify(email, "password1");
        ResultActions userSession = login(email, "password1").andExpect(status().isOk());
        String userRefresh = field(userSession, "$.refreshToken");
        String userAuth = "Bearer " + field(userSession, "$.accessToken");
        Number userId = JsonPath.read(userSession.andReturn().getResponse().getContentAsString(), "$.user.id");

        // Real login token: proves the "role" claim becomes ROLE_ADMIN
        ResultActions adminSession = login(ADMIN_EMAIL, ADMIN_PASSWORD).andExpect(status().isOk());
        String adminAuth = "Bearer " + field(adminSession, "$.accessToken");
        Number adminId = JsonPath.read(adminSession.andReturn().getResponse().getContentAsString(), "$.user.id");

        mvc.perform(patch("/api/users/" + userId).header("Authorization", adminAuth)
                        .contentType(APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        login(email, "password1").andExpect(status().isUnauthorized());
        // the access token is still unexpired, but the user is checked on every request
        mvc.perform(get("/api/me").header("Authorization", userAuth)).andExpect(status().isUnauthorized());
        json("/api/auth/refresh", "{\"refreshToken\":\"" + userRefresh + "\"}").andExpect(status().isUnauthorized());

        mvc.perform(patch("/api/users/" + adminId).header("Authorization", adminAuth)
                        .contentType(APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isConflict());
    }

    @Test
    void forgotUnknownEmailIs204AndSendsNothing() throws Exception {
        String email = uniqueEmail();
        json("/api/auth/forgot-password", "{\"email\":\"" + email + "\"}").andExpect(status().isNoContent());
        assertThat(mail.sentTo(email)).isFalse();
    }

    @Test
    void registerExistingEmailIs201() throws Exception {
        String email = uniqueEmail();
        registerAndVerify(email, "password1");
        register(email, "password1").andExpect(status().isCreated());
        login(email, "password1").andExpect(status().isOk());
    }

    @Test
    void adminCreateDuplicateEmailIs409OnEmail() throws Exception {
        String adminAuth = "Bearer " + field(login(ADMIN_EMAIL, ADMIN_PASSWORD), "$.accessToken");
        createUser(adminAuth, ADMIN_EMAIL.toUpperCase(), "USER")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.field").value("email"));
    }

    @Test
    void demotedAdminLosesAccessBeforeTokenExpires() throws Exception {
        String adminAuth = "Bearer " + field(login(ADMIN_EMAIL, ADMIN_PASSWORD), "$.accessToken");
        String email = uniqueEmail();
        Number id = JsonPath.read(createUser(adminAuth, email, "ADMIN").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        String otherAuth = "Bearer " + field(login(email, "password1"), "$.accessToken");
        mvc.perform(get("/api/users").header("Authorization", otherAuth)).andExpect(status().isOk());

        mvc.perform(patch("/api/users/" + id).header("Authorization", adminAuth)
                        .contentType(APPLICATION_JSON).content("{\"role\":\"USER\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/users").header("Authorization", otherAuth)).andExpect(status().isForbidden());
    }

    private ResultActions createUser(String auth, String email, String role) throws Exception {
        return mvc.perform(post("/api/users").header("Authorization", auth).contentType(APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"fullName\":\"X\",\"role\":\"" + role
                        + "\",\"password\":\"password1\"}"));
    }
}
