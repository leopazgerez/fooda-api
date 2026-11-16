package com.fooda.api.stock.infrastructure.out.persistence;

import com.fooda.api.core.application.port.out.StockLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Transaction-scoped advisory lock: released on commit/rollback of the caller's transaction (no own transaction). */
@Component
public class JdbcStockLock implements StockLock {

    private final JdbcTemplate jdbc;

    public JdbcStockLock(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void lock() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            // outside a transaction the xact lock would be released right after this statement
            throw new IllegalStateException("StockLock.lock() needs an active transaction");
        }
        jdbc.execute("select pg_advisory_xact_lock(7001)");
    }
}
