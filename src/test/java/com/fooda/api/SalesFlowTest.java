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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.admin.email=sales-admin@test.com", "app.admin.password=admin-pass-123"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SalesFlowTest {

    static final String ADMIN_EMAIL = "sales-admin@test.com";
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

    private ResultActions change(String auth, String path, String body) throws Exception {
        return mvc.perform(patch(path).header("Authorization", auth).contentType(APPLICATION_JSON).content(body));
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

    @Test
    void salesPaymentsCustomersAndExpenses() throws Exception {
        String auth = login();
        Setup s = setup(auth);
        send(auth, "/api/productions/" + s.production() + "/close", closeBody(s)).andExpect(status().isOk());
        int customer = create(auth, "/api/customers", "{\"firstName\":\"Ana\",\"lastName\":\"Gómez\"}");
        assertThat(stockIn(auth, s)).isEqualTo(3);

        // retail sale: stock, payment, sold and customer totals
        int sale = create(auth, "/api/sales", saleBody(s, customer, "retail", 2, ""));
        assertThat(stockIn(auth, s)).isEqualTo(1);
        String detail = body(fetch(auth, "/api/sales/" + sale).andExpect(status().isOk()));
        BigDecimal total = new BigDecimal(JsonPath.<Number>read(detail, "$.total").toString());
        assertThat(JsonPath.<List<Object>>read(detail, "$.payments")).hasSize(1);
        assertThat(new BigDecimal(JsonPath.<Number>read(detail, "$.payments[0].amount").toString()))
                .isEqualByComparingTo(total);
        assertThat(sold(auth, s)).isEqualTo(2);
        fetch(auth, "/api/customers/" + customer).andExpect(status().isOk()).andExpect(jsonPath("$.packs").value(2));

        // paid with packs: notes required, creates a "packs" expense that cannot be deleted
        send(auth, "/api/sales", saleBody(s, customer, "paid_with_packs", 1, "")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("notes"));
        int paidWithPacks = create(auth, "/api/sales", saleBody(s, customer, "paid_with_packs", 1,
                ",\"notes\":\"Canje por fletes\""));
        long generated = jdbc.queryForObject("SELECT expense_id FROM expense WHERE sale_id = ? AND deleted_at IS NULL",
                Long.class, (long) paidWithPacks);
        fetch(auth, "/api/expenses/" + generated).andExpect(status().isOk())
                .andExpect(jsonPath("$.method").value("packs")).andExpect(jsonPath("$.saleId").value(paidWithPacks));
        remove(auth, "/api/expenses/" + generated, 409);
        remove(auth, "/api/sales/" + paidWithPacks, 204);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expense WHERE sale_id = ? AND deleted_at IS NULL",
                Integer.class, (long) paidWithPacks)).isZero();

        // annul the retail sale: stock back, sold back to 0, 404, payment deleted in the db
        remove(auth, "/api/sales/" + sale, 204);
        assertThat(stockIn(auth, s)).isEqualTo(3);
        assertThat(sold(auth, s)).isZero();
        fetch(auth, "/api/sales/" + sale).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment WHERE sale_id = ? AND deleted_at IS NULL",
                Integer.class, (long) sale)).isZero();

        // manual expense in packs, then PATCH it (exercises the real save/findById)
        String created = body(send(auth, "/api/expenses", "{\"type\":\"personal\",\"detail\":\"Regalo\",\"amountPacks\":1.5}")
                .andExpect(status().isCreated()));
        int expense = JsonPath.read(created, "$.id");
        BigDecimal packPrice = new BigDecimal(JsonPath.<Number>read(created, "$.packPrice").toString());
        assertThat(new BigDecimal(JsonPath.<Number>read(created, "$.amount").toString()))
                .isEqualByComparingTo(packPrice.multiply(new BigDecimal("1.5")));
        long version = JsonPath.<Number>read(created, "$.version").longValue();

        String renamed = body(change(auth, "/api/expenses/" + expense, "{\"detail\":\"Regalo cliente\",\"version\":" + version + "}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.detail").value("Regalo cliente")));
        version = JsonPath.<Number>read(renamed, "$.version").longValue();
        change(auth, "/api/expenses/" + expense, "{\"amountPacks\":2,\"version\":" + version + "}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.amountPacks").value(2.0))
                .andExpect(jsonPath("$.packPrice").value(packPrice.doubleValue()))
                .andExpect(jsonPath("$.amount").value(packPrice.multiply(BigDecimal.valueOf(2)).doubleValue()));
        fetch(auth, "/api/expenses/" + expense).andExpect(status().isOk())
                .andExpect(jsonPath("$.detail").value("Regalo cliente")).andExpect(jsonPath("$.amountPacks").value(2.0))
                .andExpect(jsonPath("$.amount").value(packPrice.multiply(BigDecimal.valueOf(2)).doubleValue()));
    }
}
