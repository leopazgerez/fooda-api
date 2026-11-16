package com.fooda.api.production.fakes;

import com.fooda.api.core.application.port.out.StockLock;

/** Counts the calls so tests can check the lock was taken. */
public class NoopStockLock implements StockLock {

    public int calls;

    @Override public void lock() { calls++; }
}
