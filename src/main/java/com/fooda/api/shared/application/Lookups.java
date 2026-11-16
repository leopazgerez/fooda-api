package com.fooda.api.shared.application;

import com.fooda.api.shared.application.exception.ResourceNotFoundException;

import java.util.Optional;
import java.util.function.LongFunction;

public final class Lookups {

    private Lookups() {
    }

    public static <T> T found(Long id, LongFunction<Optional<T>> finder, String message) {
        if (id == null) {
            throw new ResourceNotFoundException(message);
        }
        return finder.apply(id).orElseThrow(() -> new ResourceNotFoundException(message));
    }
}
