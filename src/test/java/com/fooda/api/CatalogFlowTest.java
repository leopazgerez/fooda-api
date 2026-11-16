package com.fooda.api;

import com.fooda.api.catalog.application.service.CatalogSeed;
import com.fooda.api.shared.infrastructure.RunAs;
import com.fooda.api.support.Db;
import com.fooda.api.user.application.port.out.PasswordHasher;
import com.fooda.api.user.application.port.out.UserRepository;
import com.fooda.api.user.domain.Role;
import com.fooda.api.user.domain.User;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.admin.email=catalog-admin@test.com", "app.admin.password=admin-pass-123"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CatalogFlowTest {

    static final String ADMIN_EMAIL = "catalog-admin@test.com";
    static final String ADMIN_PASSWORD = "admin-pass-123";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordHasher hasher;
    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogSeed seed;

    /** Other test classes truncate app_user (cascading to the catalog): restore admin and seed. */
    @BeforeEach
    void ensureAdmin() {
        String hash = hasher.hash(ADMIN_PASSWORD);
        User admin = users.save(users.findByEmail(ADMIN_EMAIL)
                .map(u -> u.withPasswordHash(hash).withRole(Role.ADMIN).withActive(true))
                .orElseGet(() -> new User(null, ADMIN_EMAIL, "Admin", hash, Role.ADMIN, true, true)));
        // wiping app_user cascades to the catalog, so re-seed as the startup runner does
        RunAs.user(admin.id().value(), seed::seedIfEmpty);
    }

    @AfterEach
    void clean() {
        Db.reset(jdbc);
    }

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    private ResultActions send(String auth, String path, String body) throws Exception {
        return mvc.perform(post(path).header("Authorization", auth).contentType(APPLICATION_JSON).content(body));
    }

    private int create(String auth, String path, String body) throws Exception {
        String res = send(auth, path, body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.id");
    }

    @Test
    void ownerBuildsCatalogAndSeesPackCost() throws Exception {
        String auth = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String n = UUID.randomUUID().toString().substring(0, 8);

        String settings = mvc.perform(get("/api/settings").header("Authorization", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Number>read(settings, "$.packPrice").doubleValue()).isEqualTo(45000.0);
        // created_by is the admin that seeded first (maybe another context's admin)
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM settings s JOIN app_user u ON u.user_id = s.created_by
                WHERE u.role = 'ADMIN'""", Integer.class)).isEqualTo(1);

        int supplier = create(auth, "/api/suppliers", "{\"name\":\"Prov " + n + "\"}");
        int supply = create(auth, "/api/supplies", "{\"name\":\"Harina " + n + "\",\"unit\":\"g\",\"supplierId\":"
                + supplier + ",\"content\":1000,\"packagePrice\":9500}");
        int dish = create(auth, "/api/dishes", "{\"name\":\"Plato " + n + "\",\"recipe\":[{\"supplyId\":"
                + supply + ",\"quantity\":200,\"unit\":\"g\"}]}");
        int pack = create(auth, "/api/packs", "{\"name\":\"Pack " + n + "\"}");
        String menu = "{\"number\":%d,\"dishId\":" + dish + "}";
        String menus = String.join(",", menu.formatted(1), menu.formatted(2), menu.formatted(3),
                menu.formatted(4), menu.formatted(5));
        send(auth, "/api/packs/" + pack + "/versions", "{\"menus\":[" + menus + "]}")
                .andExpect(status().isCreated());

        String detail = mvc.perform(get("/api/packs/" + pack).header("Authorization", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Number>read(detail, "$.cost.recipes").doubleValue()).isGreaterThan(0);
        assertThat(JsonPath.<Number>read(detail, "$.salePrice").doubleValue()).isEqualTo(45000.0);

        mvc.perform(delete("/api/supplies/" + supply).header("Authorization", auth))
                .andExpect(status().isConflict());
        mvc.perform(patch("/api/supplies/" + supply).header("Authorization", auth)
                        .contentType(APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk());

        String email = "u" + n + "@mail.com";
        users.save(new User(null, email, "Ana", hasher.hash("password1"), Role.USER, true, true));
        mvc.perform(get("/api/supplies").header("Authorization", login(email, "password1")))
                .andExpect(status().isForbidden());
    }

    private ResultActions putSettings(String auth, String body) throws Exception {
        return mvc.perform(put("/api/settings").header("Authorization", auth).contentType(APPLICATION_JSON).content(body));
    }

    private ResultActions patchJson(String auth, String path, String body) throws Exception {
        return mvc.perform(patch(path).header("Authorization", auth).contentType(APPLICATION_JSON).content(body));
    }

    private static final String SETTINGS = """
            {"packPrice":46000,"oilMlPerPack":50,"oilSupplyId":null,"traysPerPack":5,"stickersPerPack":5,
             "referenceOverhead":1000,"regularMonths":3,"recontactDays":30,"priceRiseAlert":0.1,
             "saleReasons":["regalo"],"expenseTypes":["fijo"]%s}""";

    @Test
    void settingsPutRequiresEveryFieldAndHonoursVersion() throws Exception {
        String auth = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        String current = mvc.perform(get("/api/settings").header("Authorization", auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        int version = JsonPath.<Number>read(current, "$.version").intValue();

        putSettings(auth, SETTINGS.formatted("").replace("\"referenceOverhead\":1000,", ""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("referenceOverhead"));
        putSettings(auth, SETTINGS.formatted("").replace("\"saleReasons\":[\"regalo\"],", ""))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.field").value("saleReasons"));

        String saved = putSettings(auth, SETTINGS.formatted(",\"version\":" + version))
                .andExpect(status().isOk()).andExpect(jsonPath("$.packPrice").value(46000))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<Number>read(saved, "$.version").intValue()).isGreaterThan(version);

        putSettings(auth, SETTINGS.formatted(",\"version\":" + version)).andExpect(status().isConflict());
        putSettings(auth, SETTINGS.formatted("")).andExpect(status().isOk())
                .andExpect(jsonPath("$.saleReasons[0]").value("regalo"));
    }

    @Test
    void patchWithStaleVersionIs409AndCurrentVersionIs200() throws Exception {
        String auth = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        int supplier = create(auth, "/api/suppliers", "{\"name\":\"Prov lock\"}");
        String body = mvc.perform(get("/api/suppliers/" + supplier).header("Authorization", auth))
                .andReturn().getResponse().getContentAsString();
        int version = JsonPath.<Number>read(body, "$.version").intValue();

        patchJson(auth, "/api/suppliers/" + supplier, "{\"name\":\"Prov A\",\"version\":" + version + "}")
                .andExpect(status().isOk());
        patchJson(auth, "/api/suppliers/" + supplier, "{\"name\":\"Prov B\",\"version\":" + version + "}")
                .andExpect(status().isConflict());
        patchJson(auth, "/api/suppliers/" + supplier, "{\"name\":\"Prov C\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Prov C"));
    }

    @Test
    void subResourceLocationsPointToTheirCollectionAndOverflowIs400() throws Exception {
        String auth = login(ADMIN_EMAIL, ADMIN_PASSWORD);
        int supplier = create(auth, "/api/suppliers", "{\"name\":\"Prov loc\"}");
        int supply = create(auth, "/api/supplies", "{\"name\":\"Sal loc\",\"unit\":\"g\",\"supplierId\":"
                + supplier + ",\"content\":1000}");
        send(auth, "/api/supplies/" + supply + "/prices", "{\"packagePrice\":100}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/supplies/" + supply + "/prices")));
        int dish = create(auth, "/api/dishes", "{\"name\":\"Plato loc\",\"recipe\":[{\"supplyId\":"
                + supply + ",\"quantity\":10,\"unit\":\"g\"}]}");
        int pack = create(auth, "/api/packs", "{\"name\":\"Pack loc\"}");
        String menu = "{\"number\":%d,\"dishId\":" + dish + "}";
        String menus = String.join(",", menu.formatted(1), menu.formatted(2), menu.formatted(3),
                menu.formatted(4), menu.formatted(5));
        send(auth, "/api/packs/" + pack + "/versions", "{\"menus\":[" + menus + "]}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/packs/" + pack + "/versions")));

        // content is NUMERIC(12,3): Postgres raises 22003
        send(auth, "/api/supplies", "{\"name\":\"Enorme\",\"unit\":\"g\",\"supplierId\":" + supplier
                + ",\"content\":1000000000000}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Algún valor está fuera de rango"));
    }
}
