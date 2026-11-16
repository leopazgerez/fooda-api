package com.fooda.api.core.fakes;

import com.fooda.api.core.application.port.out.*;
import com.fooda.api.core.domain.catalog.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public class InMemoryReserveFundRepository implements ReserveFundRepository {

    private final Map<Long, ReserveFund> rows = new LinkedHashMap<>();
    private long seq;

    @Override public List<ReserveFund> findAll() { return List.copyOf(rows.values()); }

    @Override public Optional<ReserveFund> findById(long id) { return Optional.ofNullable(rows.get(id)); }

    @Override
    public ReserveFund save(ReserveFund v) {
        long id = v.id() != null ? v.id() : ++seq;
        var saved = new ReserveFund(id, v.name(), v.percentage(), v.version() == null ? 0L : v.version() + 1);
        rows.put(id, saved);
        return saved;
    }

    @Override public void softDelete(long id, long actorId) { rows.remove(id); }
}
