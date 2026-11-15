package com.fooda.api.shared.application;

/** PATCH field: distinguishes "absent" from "present with null". */
public record Field<T>(boolean present, T value) {

    public static <T> Field<T> absent() {
        return new Field<>(false, null);
    }

    public static <T> Field<T> of(T value) {
        return new Field<>(true, value);
    }

    public T or(T current) {
        return present ? value : current;
    }
}
