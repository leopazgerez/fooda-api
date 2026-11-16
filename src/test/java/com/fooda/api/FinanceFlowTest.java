package com.fooda.api;

import com.fooda.api.catalog.application.service.CatalogSeed;
import com.fooda.api.shared.infrastructure.RunAs;
import com.fooda.api.support.Db;
import com.fooda.api.user.application.port.out.PasswordHasher;
import com.fooda.api.user.application.port.out.UserRepository;
import com.fooda.api.user.domain.Role;
import com.fooda.api.user.domain.User;
import com.fooda.api.shared.application.BusinessCalendar;
import com.jayway.jsonpath.JsonPath;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.admin.email=finance-admin@test.com", "app.admin.password=admin-pass-123"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FinanceFlowTest {

    static final String ADMIN_EMAIL = "finance-admin@test.com";
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
    private record Setup(int pack, int jvg, int production) {
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
        return new Setup(pack, jvg, production);
    }

    private static String closeBody(Setup s) {
        return "{\"lines\":[{\"packId\":" + s.pack() + ",\"actual\":8}],\"distribution\":[{\"branchId\":" + s.jvg()
                + ",\"packId\":" + s.pack() + ",\"quantity\":3}]}";
    }

    private ResultActions replace(String auth, String path, String body) throws Exception {
        return mvc.perform(put(path).header("Authorization", auth).contentType(APPLICATION_JSON).content(body));
    }

    private String body(ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    private int stockIn(String auth, Setup s) throws Exception {
        String view = body(fetch(auth, "/api/stock?productionId=" + s.production() + "&branchId=" + s.jvg()));
        return JsonPath.<List<Integer>>read(view, "$.rows[*].quantity").stream().mapToInt(Integer::intValue).sum();
    }

    private int sold(String auth, Setup s) throws Exception {
        String list = body(fetch(auth, "/api/productions"));
        return JsonPath.<List<Integer>>read(list, "$.items[?(@.id == " + s.production() + ")].sold").get(0);
    }

    private String saleBody(Setup s, int customer, String type, int quantity, String extra) {
        return "{\"branchId\":" + s.jvg() + ",\"customerId\":" + customer + ",\"type\":\"" + type
                + "\",\"method\":\"cash\"" + extra + ",\"lines\":[{\"productionId\":" + s.production()
                + ",\"packId\":" + s.pack() + ",\"quantity\":" + quantity + "}]}";
    }

    private void remove(String auth, String path, int expected) throws Exception {
        mvc.perform(delete(path).header("Authorization", auth)).andExpect(status().is(expected));
    }

    private String closedSetup(String auth, Setup s) throws Exception {
        send(auth, "/api/productions/" + s.production() + "/close", closeBody(s)).andExpect(status().isOk());
        return YearMonth.now(BusinessCalendar.ZONE).toString();
    }

    private String expenseBody() {
        return "{\"type\":\"personal\",\"detail\":\"Regalo\",\"amount\":1000}";
    }

    private String transferBody(Setup s, int destination) {
        return "{\"originBranchId\":" + s.jvg() + ",\"destinationBranchId\":" + destination
                + ",\"lines\":[{\"productionId\":" + s.production() + ",\"packId\":" + s.pack() + ",\"quantity\":1}]}";
    }

    @Test
    void closedMonthBlocksWritesAndReopenUnblocks() throws Exception {
        String auth = login();
        Setup s = setup(auth);
        String month = closedSetup(auth, s);
        int customer = create(auth, "/api/customers", "{\"firstName\":\"Ana\",\"lastName\":\"Gómez\"}");
        int other = create(auth, "/api/branches", "{\"name\":\"Otra " + UUID.randomUUID() + "\",\"capacity\":100}");

        send(auth, "/api/finance/month-closes", "{\"month\":\"" + month + "\"}").andExpect(status().isCreated());
        for (var write : List.of(Map.entry("/api/sales", saleBody(s, customer, "retail", 1, "")),
                Map.entry("/api/expenses", expenseBody()), Map.entry("/api/transfers", transferBody(s, other)))) {
            send(auth, write.getKey(), write.getValue()).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.field").value("date"));
        }

        remove(auth, "/api/finance/month-closes/" + month, 204);
        create(auth, "/api/sales", saleBody(s, customer, "retail", 1, ""));
        create(auth, "/api/expenses", expenseBody());
        create(auth, "/api/transfers", transferBody(s, other));
    }

    @Test
    void closedMonthReadsTheSnapshot() throws Exception {
        String auth = login();
        Setup s = setup(auth);
        String month = closedSetup(auth, s);
        int customer = create(auth, "/api/customers", "{\"firstName\":\"Ana\",\"lastName\":\"Gómez\"}");
        create(auth, "/api/sales", saleBody(s, customer, "retail", 2, ""));
        create(auth, "/api/reserve-funds", "{\"name\":\"Base " + UUID.randomUUID() + "\",\"percentage\":10}");

        String closed = body(send(auth, "/api/finance/month-closes", "{\"month\":\"" + month + "\"}")
                .andExpect(status().isCreated()));

        String settings = body(fetch(auth, "/api/settings").andExpect(status().isOk()));
        BigDecimal price = new BigDecimal(JsonPath.<Number>read(settings, "$.packPrice").toString());
        Map<String, Object> changed = new HashMap<>(JsonPath.<Map<String, Object>>read(settings, "$"));
        changed.put("packPrice", price.multiply(BigDecimal.valueOf(2)));
        replace(auth, "/api/settings", new ObjectMapper().writeValueAsString(changed))
                .andExpect(status().isOk());

        create(auth, "/api/reserve-funds", "{\"name\":\"Nuevo " + UUID.randomUUID() + "\",\"percentage\":7}");

        String monthly = body(fetch(auth, "/api/finance/monthly").andExpect(status().isOk()));
        String row = "$[?(@.month == '" + month + "')]";
        assertThat(JsonPath.<List<Boolean>>read(monthly, row + ".closed")).containsExactly(true);
        assertThat(JsonPath.<List<Object>>read(monthly, row + ".profitInPacks").get(0).toString())
                .isEqualTo(String.valueOf(JsonPath.<Object>read(closed, "$.profitInPacks")));
        assertThat(JsonPath.<List<Object>>read(monthly, row + ".reserves").get(0))
                .isEqualTo(JsonPath.<Object>read(closed, "$.reserves"));

        // control: once reopened the same month is live and must reflect the changes
        remove(auth, "/api/finance/month-closes/" + month, 204);
        String live = body(fetch(auth, "/api/finance/monthly").andExpect(status().isOk()));
        assertThat(JsonPath.<List<Boolean>>read(live, row + ".closed")).containsExactly(false);
        assertThat(JsonPath.<List<Object>>read(live, row + ".reserves").get(0))
                .isNotEqualTo(JsonPath.<Object>read(closed, "$.reserves"));
        assertThat(JsonPath.<List<Object>>read(live, row + ".profitInPacks").get(0).toString())
                .isNotEqualTo(String.valueOf(JsonPath.<Object>read(closed, "$.profitInPacks")));
    }

    @Test
    void dashboardAndAnalysisRespond() throws Exception {
        String auth = login();
        Setup s = setup(auth);
        String month = closedSetup(auth, s);
        int customer = create(auth, "/api/customers", "{\"firstName\":\"Ana\",\"lastName\":\"Gómez\"}");
        create(auth, "/api/sales", saleBody(s, customer, "retail", 1, ""));

        fetch(auth, "/api/dashboard").andExpect(status().isOk())
                .andExpect(jsonPath("$.stockByBranch").isNotEmpty()).andExpect(jsonPath("$.month.month").value(month));
        fetch(auth, "/api/analysis/packs?groupBy=month").andExpect(status().isOk())
                .andExpect(jsonPath("$.rows").isNotEmpty());

        String email = "finance-user@test.com";
        users.save(users.findByEmail(email).orElseGet(() ->
                new User(null, email, "User", hasher.hash("user-pass-123"), Role.USER, true, true)));
        String token = JsonPath.read(body(send("", "/api/auth/login",
                "{\"email\":\"" + email + "\",\"password\":\"user-pass-123\"}").andExpect(status().isOk())), "$.accessToken");
        fetch("Bearer " + token, "/api/dashboard").andExpect(status().isForbidden());
    }
}
