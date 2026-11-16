package com.fooda.api.finance.application.port.in;

import com.fooda.api.core.domain.catalog.PackVersion;

import java.time.LocalDate;
import java.util.List;

/** {@code until} is the next version's start, or null; {@code previous} is null for the first version. */
public record VersionAt(PackVersion version, LocalDate until, List<Menu> menus, PackVersion previous,
                        List<Change> changes) {

    public record Menu(int number, String dish) {
    }

    public record Change(int number, String before, String after, int newNumber) {
    }
}
