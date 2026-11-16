package com.fooda.api.core.application.port.out;

public interface StockLock {

    /** Serializes stock-moving writes; must be called inside the service's transaction. */
    void lock();
}
