package com.fooda.api.catalog.fakes;

import com.fooda.api.core.application.port.out.*;
import com.fooda.api.core.domain.catalog.*;

import java.util.*;

public class InMemorySupplierRepository implements SupplierRepository {

    private final Map<Long, Supplier> rows = new LinkedHashMap<>();
    private long seq;

    @Override public List<Supplier> findAll() { return List.copyOf(rows.values()); }

    @Override public Optional<Supplier> findById(long id) { return Optional.ofNullable(rows.get(id)); }

    @Override
    public Supplier save(Supplier v) {
        long id = v.id() != null ? v.id() : ++seq;
        var saved = new Supplier(id, v.name(), v.purchaseDay(), v.sortOrder(), v.version() == null ? 0L : v.version() + 1);
        rows.put(id, saved);
        return saved;
    }

    @Override public void softDelete(long id, long actorId) { rows.remove(id); }
}
