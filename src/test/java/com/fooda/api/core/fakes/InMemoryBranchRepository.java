package com.fooda.api.core.fakes;

import com.fooda.api.core.application.port.out.*;
import com.fooda.api.core.domain.catalog.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

public class InMemoryBranchRepository implements BranchRepository {

    private final Map<Long, Branch> rows = new LinkedHashMap<>();
    private long seq;

    @Override public List<Branch> findAll() { return List.copyOf(rows.values()); }

    @Override public Optional<Branch> findById(long id) { return Optional.ofNullable(rows.get(id)); }

    @Override
    public Branch save(Branch v) {
        long id = v.id() != null ? v.id() : ++seq;
        var saved = new Branch(id, v.name(), v.address(), v.capacity(), v.stockThreshold(), v.freeSlotsThreshold(),
                v.shipping(), v.affectsPrice(), v.kitchen(), v.version() == null ? 0L : v.version() + 1);
        rows.put(id, saved);
        return saved;
    }

    @Override public void softDelete(long id, long actorId) { rows.remove(id); }

    @Override public Optional<Branch> findKitchen() { return rows.values().stream().filter(Branch::kitchen).findFirst(); }
}
