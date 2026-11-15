package com.fooda.api.shared.application.port.out;

import java.util.function.Supplier;

/** Runs work in one transaction; the core never uses Spring's annotations. */
public interface Transactions {

    void run(Runnable work);

    <T> T get(Supplier<T> work);
}
