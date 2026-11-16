package com.fooda.api.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** Test isolation: the Postgres container is shared, so committed rows (seed, flow tests) must not leak. */
public final class Db {

    private Db() {
    }

    /** Empties app_user and, by cascade, every table that references it (catalog, tokens). Transactional in Postgres. */
    public static void reset(JdbcTemplate jdbc) {
        jdbc.update("TRUNCATE app_user CASCADE");
    }
}
