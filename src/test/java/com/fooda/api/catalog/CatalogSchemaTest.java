package com.fooda.api.catalog;

import com.fooda.api.TestcontainersConfiguration;
import com.fooda.api.support.Actors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class CatalogSchemaTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void onlyOneLiveKitchen() {
        long u = Actors.insertUser(jdbc);
        insertBranch(u, "Salta", true);
        rejected(() -> insertBranch(u, "Otra", true));
        jdbc.update("update branch set deleted_at = now(), deleted_by = ? where name = 'Salta'", u);
        insertBranch(u, "Otra", true);
    }

    @Test
    void supplyNameUniqueIgnoringCaseAmongLiveRows() {
        long u = Actors.insertUser(jdbc);
        long s = insertSupplier(u);
        insertSupply(u, s, "Pollo");
        rejected(() -> insertSupply(u, s, "POLLO"));
        jdbc.update("update supply set deleted_at = now(), deleted_by = ? where name = 'Pollo'", u);
        insertSupply(u, s, "pollo");
    }

    @Test
    void expenseTypesMustKeepFijo() {
        long u = Actors.insertUser(jdbc);
        rejected(() -> insertSettings(u, "{mercadería}"));
    }

    /** Postgres aborts the transaction on error; a savepoint keeps it usable for the following statements. */
    private void rejected(Runnable insert) {
        jdbc.execute("savepoint sp");
        assertThatThrownBy(insert::run).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.execute("rollback to savepoint sp");
    }

    private void insertBranch(long u, String name, boolean kitchen) {
        jdbc.update("insert into branch (name, capacity, is_kitchen, created_at, created_by, updated_at, updated_by)"
                + " values (?, 10, ?, now(), ?, now(), ?)", name, kitchen, u, u);
    }

    private long insertSupplier(long u) {
        return jdbc.queryForObject("insert into supplier (name, created_at, created_by, updated_at, updated_by)"
                + " values ('Prov', now(), ?, now(), ?) returning supplier_id", Long.class, u, u);
    }

    private void insertSupply(long u, long supplierId, String name) {
        jdbc.update("insert into supply (name, unit, supplier_id, content, created_at, created_by, updated_at, updated_by)"
                + " values (?, 'g', ?, 1000, now(), ?, now(), ?)", name, supplierId, u, u);
    }

    private void insertSettings(long u, String expenseTypes) {
        jdbc.update("insert into settings (pack_price, oil_ml_per_pack, trays_per_pack, stickers_per_pack,"
                + " reference_overhead, regular_months, recontact_days, price_rise_alert, sale_reasons, expense_types,"
                + " created_at, created_by, updated_at, updated_by)"
                + " values (1, 1, 1, 1, 0, 3, 30, 0.1, '{a}', ?::text[], now(), ?, now(), ?)", expenseTypes, u, u);
    }
}
