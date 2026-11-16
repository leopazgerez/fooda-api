package com.fooda.api.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.atomic.AtomicInteger;

/** Raw FK parents for persistence tests of one area, so they need no adapter of another. */
public final class Rows {

    private static final AtomicInteger SEQ = new AtomicInteger();
    private static final String AUDIT = "now(), ?, now(), ?";

    private Rows() {
    }

    public static long pack(JdbcTemplate jdbc, long actor) {
        return jdbc.queryForObject("insert into pack (number, name, created_at, created_by, updated_at, updated_by)"
                + " values (?, 'Pack', " + AUDIT + ") returning pack_id", Long.class, SEQ.incrementAndGet(), actor, actor);
    }

    public static long supplier(JdbcTemplate jdbc, long actor) {
        return jdbc.queryForObject("insert into supplier (name, created_at, created_by, updated_at, updated_by)"
                + " values ('Súper', " + AUDIT + ") returning supplier_id", Long.class, actor, actor);
    }

    public static long supply(JdbcTemplate jdbc, long actor, long supplierId) {
        return jdbc.queryForObject("insert into supply (name, unit, supplier_id, content, created_at, created_by, updated_at, updated_by)"
                + " values (?, 'g', ?, 1000, " + AUDIT + ") returning supply_id", Long.class,
                "Insumo " + SEQ.incrementAndGet(), supplierId, actor, actor);
    }

    public static long branch(JdbcTemplate jdbc, long actor) {
        return jdbc.queryForObject("insert into branch (name, capacity, created_at, created_by, updated_at, updated_by)"
                + " values ('Sucursal', 10, " + AUDIT + ") returning branch_id", Long.class, actor, actor);
    }

    public static long production(JdbcTemplate jdbc, long actor) {
        int n = SEQ.incrementAndGet();
        return jdbc.queryForObject("insert into production (code, number, status, start_date, snapshot, created_at, created_by, updated_at, updated_by)"
                + " values (?, ?, 'planned', date '2026-01-01', '{}', " + AUDIT + ") returning production_id",
                Long.class, "P" + n, n, actor, actor);
    }

    public static long customer(JdbcTemplate jdbc, long actor) {
        return jdbc.queryForObject("insert into customer (first_name, last_name, created_at, created_by, updated_at, updated_by)"
                + " values ('Ana', 'Pérez', " + AUDIT + ") returning customer_id", Long.class, actor, actor);
    }
}
