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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.admin.email=stock-admin@test.com", "app.admin.password=admin-pass-123"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StockFlowTest {

    static final String ADMIN_EMAIL = "stock-admin@test.com";
    static final String ADMIN_PASSWORD = "admin-pass-123";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordHasher hasher;
    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogSeed seed;

    @BeforeEach
    void ensureAdmin() {
        String hash = hasher.hash(ADMIN_PASSWORD);
        User admin = users.save(users.findByEmail(ADMIN_EMAIL)
                .map(u -> u.withPasswordHash(hash).withRole(Role.ADMIN).withActive(true))
                .orElseGet(() -> new User(null, ADMIN_EMAIL, "Admin", hash, Role.ADMIN, true, true)));
        RunAs.user(admin.id().value(), seed::seedIfEmpty);
    }

    @AfterEach
    void clean() {
        Db.reset(jdbc);
    }

    private String login() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(APPLICATION_JSON)
                        .content("{\"email\":\"" + ADMIN_EMAIL + "\",\"password\":\"" + ADMIN_PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    private ResultActions send(String auth, String path, String body) throws Exception {
        return mvc.perform(post(path).header("Authorization", auth).contentType(APPLICATION_JSON).content(body));
    }

    private ResultActions fetch(String auth, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", auth));
    }

    private int create(String auth, String path, String body) throws Exception {
        String res = send(auth, path, body).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(res, "$.id");
    }

    /** Everything the flow needs, created through the API. */
    private record Setup(int supplier, int pack, int jvg, int production, int flour) {
    }

    private Setup setup(String auth) throws Exception {
        String n = UUID.randomUUID().toString().substring(0, 8);
        int supplier = create(auth, "/api/suppliers", "{\"name\":\"Prov " + n + "\"}");
        int flour = create(auth, "/api/supplies", "{\"name\":\"Harina " + n + "\",\"unit\":\"g\",\"supplierId\":"
                + supplier + ",\"content\":1000,\"packagePrice\":9500}");
        int tray = create(auth, "/api/supplies", "{\"name\":\"Bandeja " + n + "\",\"unit\":\"u\",\"supplierId\":"
                + supplier + ",\"content\":50,\"packagePrice\":5000,\"packagingType\":\"tray\"}");
        int dish = create(auth, "/api/dishes", "{\"name\":\"Plato " + n + "\",\"recipe\":[{\"supplyId\":" + flour
                + ",\"quantity\":200,\"unit\":\"g\"},{\"supplyId\":" + tray + ",\"quantity\":1,\"unit\":\"u\"}]}");
        int pack = create(auth, "/api/packs", "{\"name\":\"Pack " + n + "\"}");
        String menu = "{\"number\":%d,\"dishId\":" + dish + "}";
        String menus = String.join(",", menu.formatted(1), menu.formatted(2), menu.formatted(3),
                menu.formatted(4), menu.formatted(5));
        send(auth, "/api/packs/" + pack + "/versions", "{\"menus\":[" + menus + "]}").andExpect(status().isCreated());
        int jvg = create(auth, "/api/branches", "{\"name\":\"JVG " + n + "\",\"capacity\":100}");
        int production = create(auth, "/api/productions", "{\"lines\":[{\"packId\":" + pack + ",\"planned\":10}]}");
        return new Setup(supplier, pack, jvg, production, flour);
    }

    private static String closeBody(Setup s) {
        return "{\"lines\":[{\"packId\":" + s.pack() + ",\"actual\":8}],\"distribution\":[{\"branchId\":" + s.jvg()
                + ",\"packId\":" + s.pack() + ",\"quantity\":3}]}";
    }

    private int manualTransfer(String auth, Setup s, long kitchen) throws Exception {
        return create(auth, "/api/transfers", "{\"originBranchId\":\"" + kitchen + "\",\"destinationBranchId\":" + s.jvg()
                + ",\"lines\":[{\"productionId\":" + s.production() + ",\"packId\":" + s.pack() + ",\"quantity\":2}]}");
    }

    private void remove(String auth, String path, int expected) throws Exception {
        mvc.perform(delete(path).header("Authorization", auth)).andExpect(status().is(expected));
    }

    @Test
    void stockTransfersAdjustmentsAndReopen() throws Exception {
        String auth = login();
        Setup s = setup(auth);
        send(auth, "/api/productions/" + s.production() + "/close", closeBody(s)).andExpect(status().isOk());
        long kitchen = jdbc.queryForObject("SELECT branch_id FROM branch WHERE is_kitchen AND deleted_at IS NULL", Long.class);

        String view = fetch(auth, "/api/stock?productionId=" + s.production()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<Integer>>read(view, "$.rows[*].quantity")).containsExactly(5, 3);
        assertThat(JsonPath.<List<Number>>read(view, "$.rows[*].branchId").stream().map(Number::longValue).toList())
                .containsExactly(kitchen, (long) s.jvg());
        assertThat(JsonPath.<List<Integer>>read(view, "$.byBranch[*].total")).containsExactlyInAnyOrder(5, 3);

        // manual transfer: create + annul
        int manual = manualTransfer(auth, s, kitchen);
        remove(auth, "/api/transfers/" + manual, 204);

        // the distribution transfer cannot be annulled on its own
        long delivery = jdbc.queryForObject("SELECT transfer_id FROM transfer WHERE source_production_id = ?", Long.class, s.production());
        remove(auth, "/api/transfers/" + delivery, 409);

        // adjustment without lot creates INICIAL
        int adjustment = create(auth, "/api/stock-adjustments", "{\"branchId\":" + s.jvg() + ",\"packId\":" + s.pack()
                + ",\"quantity\":4,\"reason\":\"Conteo inicial\"}");
        String productions = fetch(auth, "/api/productions").andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(productions, "$.items[*].code")).contains("INICIAL");
        assertThat(JsonPath.<List<Integer>>read(productions, "$.items[?(@.code == 'INICIAL')].number")).containsExactly(0);
        assertThat(JsonPath.<List<Integer>>read(productions, "$.items[?(@.code == 'INICIAL')].packs")).containsExactly(0);
        remove(auth, "/api/stock-adjustments/" + adjustment, 204);

        // a live manual transfer out of the lot blocks the reopening
        int again = manualTransfer(auth, s, kitchen);
        send(auth, "/api/productions/" + s.production() + "/reopen", "{}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Los packs de este lote ya se movieron; no se puede reabrir"));
        remove(auth, "/api/transfers/" + again, 204);
        send(auth, "/api/productions/" + s.production() + "/reopen", "{}").andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT coalesce(sum(quantity), 0) FROM stock_movement WHERE production_id = ? AND deleted_at IS NULL",
                Integer.class, s.production())).isZero();
    }
}
