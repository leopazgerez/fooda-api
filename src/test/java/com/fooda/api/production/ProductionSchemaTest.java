package com.fooda.api.production;

import com.fooda.api.TestcontainersConfiguration;
import com.fooda.api.support.Actors;
import com.fooda.api.support.Db;
import com.fooda.api.support.Rows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class ProductionSchemaTest {

    private static final List<String> TABLES = List.of("production", "production_line", "purchase",
            "purchase_line", "transfer", "transfer_line", "stock_movement", "expense");
    private static final List<String> AUDIT = List.of("created_at", "created_by", "updated_at", "updated_by",
            "deleted_at", "deleted_by", "version");

    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        Db.reset(jdbc);
    }

    @Test
    void everyTableHasFullAuditBlock() {
        for (String table : TABLES) {
            List<String> cols = jdbc.queryForList("select column_name from information_schema.columns"
                    + " where table_schema = current_schema() and table_name = ?", String.class, table);
            assertThat(cols).as(table).containsAll(AUDIT).contains(table + "_id");
        }
    }

    @Test
    void closedNeedsClosingDate() {
        long u = Actors.insertUser(jdbc);
        rejected(() -> insertProduction(u, "P1", "closed", null));
        insertProduction(u, "P1", "planned", null);
        insertProduction(u, "P2", "closed", "2026-01-01");
    }

    @Test
    void oneLivePendingPurchasePerSupplierAndProduction() {
        long u = Actors.insertUser(jdbc);
        long s = insertSupplier(u);
        long p = insertProduction(u, "P1", "planned", null);
        long first = insertPurchase(u, p, s);
        rejected(() -> insertPurchase(u, p, s));
        jdbc.update("update purchase set deleted_at = now(), deleted_by = ? where purchase_id = ?", u, first);
        insertPurchase(u, p, s);
        insertPurchase(u, null, s);
        insertPurchase(u, null, s);
    }

    @Test
    void movementQuantityCannotBeZero() {
        long u = Actors.insertUser(jdbc);
        long b = insertBranch(u);
        long p = insertProduction(u, "P1", "planned", null);
        long pk = insertPack(u);
        rejected(() -> insertMovement(u, b, p, pk, 0));
        insertMovement(u, b, p, pk, -3);
    }

    @Test
    void purchaseLineQuantityMustBePositive() {
        long u = Actors.insertUser(jdbc);
        long s = insertSupplier(u);
        long supply = Rows.supply(jdbc, u, s);
        long purchase = insertPurchase(u, null, s);
        String insert = "insert into purchase_line (purchase_id, supply_id, quantity, package_price, created_at,"
                + " created_by, updated_at, updated_by) values (?, ?, ?, 10, now(), ?, now(), ?)";
        rejected(() -> jdbc.update(insert, purchase, supply, 0, u, u));
        jdbc.update(insert, purchase, supply, 0.001, u, u);
    }

    @Test
    void transferNeedsTwoBranchesAndPositiveLines() {
        long u = Actors.insertUser(jdbc);
        long a = insertBranch(u);
        long b = insertBranch(u);
        long p = insertProduction(u, "P1", "planned", null);
        long pk = insertPack(u);
        rejected(() -> insertTransfer(u, a, a));
        long t = insertTransfer(u, a, b);
        String line = "insert into transfer_line (transfer_id, production_id, pack_id, quantity, created_at,"
                + " created_by, updated_at, updated_by) values (?, ?, ?, ?, now(), ?, now(), ?)";
        rejected(() -> jdbc.update(line, t, p, pk, 0, u, u));
        rejected(() -> jdbc.update(line, t, p, pk, -1, u, u));
        jdbc.update(line, t, p, pk, 1, u, u);
    }

    @Test
    void expensePacksPairAndPositiveAmount() {
        long u = Actors.insertUser(jdbc);
        rejected(() -> insertExpense(u, "0", null, null));
        rejected(() -> insertExpense(u, "10", "5", null));
        insertExpense(u, "10", "5", "2");
        insertExpense(u, "10", null, null);
    }

    /** Postgres aborts the transaction on error; a savepoint keeps it usable for the following statements. */
    private void rejected(Runnable insert) {
        jdbc.execute("savepoint sp");
        assertThatThrownBy(insert::run).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.execute("rollback to savepoint sp");
    }

    private long insertProduction(long u, String code, String status, String closedOn) {
        return jdbc.queryForObject("insert into production (code, number, status, start_date, closed_on, snapshot,"
                + " created_at, created_by, updated_at, updated_by)"
                + " values (?, 1, ?, date '2026-01-01', ?::date, '{}'::jsonb, now(), ?, now(), ?) returning production_id",
                Long.class, code, status, closedOn, u, u);
    }

    private long insertSupplier(long u) {
        return jdbc.queryForObject("insert into supplier (name, created_at, created_by, updated_at, updated_by)"
                + " values ('Prov', now(), ?, now(), ?) returning supplier_id", Long.class, u, u);
    }

    private long insertPurchase(long u, Long productionId, long supplierId) {
        return jdbc.queryForObject("insert into purchase (production_id, supplier_id, status, created_at, created_by,"
                + " updated_at, updated_by) values (?, ?, 'pending', now(), ?, now(), ?) returning purchase_id",
                Long.class, productionId, supplierId, u, u);
    }

    private long insertTransfer(long u, long origin, long destination) {
        return jdbc.queryForObject("insert into transfer (transfer_date, origin_branch_id, destination_branch_id,"
                + " created_at, created_by, updated_at, updated_by) values (date '2026-01-01', ?, ?, now(), ?, now(), ?)"
                + " returning transfer_id", Long.class, origin, destination, u, u);
    }

    private long insertBranch(long u) {
        return jdbc.queryForObject("insert into branch (name, capacity, created_at, created_by, updated_at, updated_by)"
                + " values ('B', 10, now(), ?, now(), ?) returning branch_id", Long.class, u, u);
    }

    private long insertPack(long u) {
        return jdbc.queryForObject("insert into pack (number, name, created_at, created_by, updated_at, updated_by)"
                + " values (1, 'Pack', now(), ?, now(), ?) returning pack_id", Long.class, u, u);
    }

    private void insertMovement(long u, long branch, long production, long pack, int qty) {
        jdbc.update("insert into stock_movement (movement_date, branch_id, production_id, pack_id, quantity, type,"
                + " created_at, created_by, updated_at, updated_by)"
                + " values (date '2026-01-01', ?, ?, ?, ?, 'adjustment', now(), ?, now(), ?)",
                branch, production, pack, qty, u, u);
    }

    private void insertExpense(long u, String amount, String amountPacks, String packPrice) {
        jdbc.update("insert into expense (expense_date, type, detail, amount, amount_packs, pack_price, method,"
                + " created_at, created_by, updated_at, updated_by)"
                + " values (date '2026-01-01', 'fijo', 'x', ?::numeric, ?::numeric, ?::numeric, 'cash', now(), ?, now(), ?)",
                amount, amountPacks, packPrice, u, u);
    }
}
