package com.fooda.api.production.application.port.in;

import java.time.LocalDate;
import java.util.List;

/** {@code date} null = today; a pack without {@link Actual} closes with its planned quantity. */
public record CloseRequest(LocalDate date, List<Actual> lines, List<Distribution> distribution) {

    public CloseRequest {
        lines = lines == null ? List.of() : List.copyOf(lines);
        distribution = distribution == null ? List.of() : List.copyOf(distribution);
    }

    public record Actual(long packId, int actual) {
    }

    public record Distribution(long branchId, long packId, int quantity) {
    }
}
