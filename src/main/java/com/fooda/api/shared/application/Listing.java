package com.fooda.api.shared.application;

import com.fooda.api.shared.domain.ValidationException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class Listing {

    private Listing() {
    }

    // ponytail: sorts/slices in memory; move to DB paging if a list outgrows a few thousand rows.
    public static <T> Page<T> page(List<T> items, ListQuery q, Map<String, Comparator<T>> sortable,
                                   String defaultSort, String defaultDir, Comparator<T> tieBreak) {
        Comparator<T> by = sortable.get(q.sort() != null ? q.sort() : defaultSort);
        if (by == null) throw new ValidationException("sort", "Orden inválido");
        if ("desc".equals(q.dir() != null ? q.dir() : defaultDir)) by = by.reversed();
        var sorted = items.stream().sorted(by.thenComparing(tieBreak)).toList();
        long from = (long) q.page() * q.size();
        var slice = from >= sorted.size() ? List.<T>of()
                : sorted.subList((int) from, (int) Math.min(from + q.size(), sorted.size()));
        return new Page<>(slice, q.page(), q.size(), items.size());
    }
}
