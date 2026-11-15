package com.fooda.api.shared;

import com.fooda.api.shared.application.port.out.Transactions;

import java.util.function.Supplier;

public class DirectTransactions implements Transactions {

    @Override
    public void run(Runnable work) {
        work.run();
    }

    @Override
    public <T> T get(Supplier<T> work) {
        return work.get();
    }
}
