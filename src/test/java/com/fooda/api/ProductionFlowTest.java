package com.fooda.api;

import com.fooda.api.catalog.application.service.CatalogSeed;
import com.fooda.api.shared.application.BusinessCalendar;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.admin.email=production-admin@test.com", "app.admin.password=admin-pass-123"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProductionFlowTest {

    static final String ADMIN_EMAIL = "production-admin@test.com";
    static final String ADMIN_PASSWORD = "admin-pass-123";
    static final String[] MONTHS = {"ENE", "FEB", "MAR", "ABR", "MAY", "JUN", "JUL", "AGO", "SEP", "OCT", "NOV", "DIC"};

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordHasher hasher;
    @Autowired JdbcTemplate jdbc;
    @Autowired CatalogSeed seed;
    @Autowired BusinessCalendar calendar;

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

    private Map<Long, Integer> stockByBranch(int production, int pack) {
        return jdbc.queryForList("""
                        SELECT branch_id, SUM(quantity) AS q FROM stock_movement
                        WHERE production_id = ? AND pack_id = ? AND deleted_at IS NULL GROUP BY branch_id""",
                production, pack).stream().collect(Collectors.toMap(r -> ((Number) r.get("branch_id")).longValue(),
                r -> ((Number) r.get("q")).intValue()));
    }

    private static String closeBody(Setup s) {
        return "{\"lines\":[{\"packId\":" + s.pack() + ",\"actual\":8}],\"distribution\":[{\"branchId\":" + s.jvg()
                + ",\"packId\":" + s.pack() + ",\"quantity\":3}]}";
    }

    @Test
    void ownerPlansBuysClosesAndReopens() throws Exception {
        String auth = login();
        Setup s = setup(auth);

        LocalDate today = calendar.today();
        String suffix = " " + MONTHS[today.getMonthValue() - 1] + " " + "%02d".formatted(today.getYear() % 100);
        String planned = fetch(auth, "/api/productions/" + s.production()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<String>read(planned, "$.code")).endsWith(suffix);
        assertThat(JsonPath.<String>read(planned, "$.status")).isEqualTo("planned");

        String pending = fetch(auth, "/api/purchases?productionId=" + s.production()).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<?>>read(pending, "$.items")).hasSize(1);
        assertThat(JsonPath.<String>read(pending, "$.items[0].status")).isEqualTo("pending");
        int purchase = JsonPath.read(pending, "$.items[0].id");
        BigDecimal estimated = new BigDecimal(JsonPath.<Number>read(planned, "$.estimatedCost").toString());

        // buy at +20 % on the priced supply (the tray price stays) -> price-rise alert
        List<String> lines = new ArrayList<>();
        int count = JsonPath.<List<?>>read(pending, "$.items[0].lines").size();
        for (int i = 0; i < count; i++) {
            String base = "$.items[0].lines[" + i + "]";
            BigDecimal price = new BigDecimal(JsonPath.<Number>read(pending, base + ".packagePrice").toString());
            if (!JsonPath.<String>read(pending, base + ".supply").startsWith("Bandeja")) {
                price = price.multiply(new BigDecimal("1.2")).setScale(0, RoundingMode.HALF_UP);
            }
            lines.add("{\"supplyId\":" + JsonPath.<Number>read(pending, base + ".supplyId")
                    + ",\"quantity\":" + JsonPath.<Number>read(pending, base + ".quantity")
                    + ",\"packagePrice\":" + price.toPlainString() + "}");
        }
        String bought = send(auth, "/api/purchases/" + purchase + "/buy", "{\"lines\":[" + String.join(",", lines) + "]}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.purchase.status").value("bought"))
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<?>>read(bought, "$.alerts")).isNotEmpty();
        String afterBuy = fetch(auth, "/api/productions/" + s.production()).andReturn().getResponse().getContentAsString();
        assertThat(new BigDecimal(JsonPath.<Number>read(afterBuy, "$.actualCost").toString())).isGreaterThan(estimated);

        send(auth, "/api/productions/" + s.production() + "/close", closeBody(s))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("closed"));
        long kitchen = jdbc.queryForObject("SELECT branch_id FROM branch WHERE is_kitchen AND deleted_at IS NULL", Long.class);
        assertThat(stockByBranch(s.production(), s.pack())).containsEntry(kitchen, 5).containsEntry((long) s.jvg(), 3);

        send(auth, "/api/productions/" + s.production() + "/reopen", "{}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("planned"));
        assertThat(stockByBranch(s.production(), s.pack())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_movement WHERE production_id = ? AND deleted_at IS NOT NULL",
                Integer.class, s.production())).isEqualTo(3);

        mvc.perform(delete("/api/purchases/" + purchase).header("Authorization", auth))
                .andExpect(status().isNoContent());
        fetch(auth, "/api/purchases?productionId=" + s.production()).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("pending"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expense WHERE purchase_id = ? AND deleted_at IS NOT NULL",
                Integer.class, purchase)).isEqualTo(1);

        fetch(auth, "/api/packaging").andExpect(status().isOk());
    }

    @Test
    void closeTwiceIs409AndReopenPlannedIs409() throws Exception {
        String auth = login();
        Setup s = setup(auth);
        String path = "/api/productions/" + s.production();

        send(auth, path + "/reopen", "{}").andExpect(status().isConflict());
        fetch(auth, path).andExpect(jsonPath("$.status").value("planned"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_movement WHERE production_id = ?", Integer.class,
                s.production())).isZero();
        send(auth, path + "/close", closeBody(s)).andExpect(status().isOk());
        Map<Long, Integer> stock = stockByBranch(s.production(), s.pack());
        send(auth, path + "/close", closeBody(s)).andExpect(status().isConflict());
        fetch(auth, path).andExpect(jsonPath("$.status").value("closed"))
                .andExpect(jsonPath("$.packLines[0].actual").value(8));
        assertThat(stockByBranch(s.production(), s.pack())).isEqualTo(stock).isNotEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM transfer WHERE source_production_id = ?", Integer.class,
                s.production())).isEqualTo(1);
    }

    @Test
    void staleLeftoversVersionIs409() throws Exception {
        String auth = login();
        Setup s = setup(auth);
        String path = "/api/productions/" + s.production();
        int version = JsonPath.read(fetch(auth, path).andReturn().getResponse().getContentAsString(), "$.version");

        mvc.perform(patch(path + "/leftovers").header("Authorization", auth).contentType(APPLICATION_JSON)
                        .content("{\"leftovers\":{\"" + s.flour() + "\":100},\"version\":" + version + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(version + 1));
        mvc.perform(patch(path + "/leftovers").header("Authorization", auth).contentType(APPLICATION_JSON)
                        .content("{\"leftovers\":{},\"version\":" + version + "}"))
                .andExpect(status().isConflict());
        fetch(auth, path).andExpect(jsonPath("$.version").value(version + 1));
    }

    @Test
    void looseBuyLocationResolvesAndUsedCatalogRowsCannotBeDeleted() throws Exception {
        String auth = login();
        Setup s = setup(auth);
        int flour = s.flour();

        String location = send(auth, "/api/purchases", "{\"supplierId\":" + s.supplier() + ",\"lines\":[{\"supplyId\":"
                + flour + ",\"quantity\":1000,\"packagePrice\":9500}]}")
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        assertThat(location).isNotNull();
        mvc.perform(get(location).header("Authorization", auth)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("bought")).andExpect(jsonPath("$.total").value(9500));
        fetch(auth, "/api/purchases/999999").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Compra no encontrada"));

        mvc.perform(delete("/api/suppliers/" + s.supplier()).header("Authorization", auth))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/packs/" + s.pack()).header("Authorization", auth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El pack está en uso; desactivalo en lugar de borrarlo"));
        send(auth, "/api/productions/" + s.production() + "/close", closeBody(s)).andExpect(status().isOk());
        mvc.perform(delete("/api/branches/" + s.jvg()).header("Authorization", auth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("La sede tiene movimientos de stock"));
        fetch(auth, "/api/branches/" + s.jvg()).andExpect(status().isOk());
        fetch(auth, "/api/packs/" + s.pack()).andExpect(status().isOk());
    }
}
