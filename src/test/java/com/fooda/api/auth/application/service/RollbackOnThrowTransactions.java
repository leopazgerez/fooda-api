package com.fooda.api.auth.application.service;

import com.fooda.api.shared.application.port.out.Transactions;

import java.time.Instant;
import java.util.List;
import java.util.function.Supplier;

/** Like a real transaction for refresh tokens: a throw undoes the revokes made inside. */
class RollbackOnThrowTransactions implements Transactions {

    private final InMemoryRefreshTokens tokens;

    RollbackOnThrowTransactions(InMemoryRefreshTokens tokens) {
        this.tokens = tokens;
    }

    @Override
    public void run(Runnable work) {
        get(() -> {
            work.run();
            return null;
        });
    }

    @Override
    public <T> T get(Supplier<T> work) {
        List<Instant> before = tokens.rows.stream().map(r -> r.revokedAt).toList();
        try {
            return work.get();
        } catch (RuntimeException e) {
            for (int i = 0; i < before.size(); i++) tokens.rows.get(i).revokedAt = before.get(i);
            throw e;
        }
    }
}
