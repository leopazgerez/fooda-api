package com.fooda.api.catalog.fakes;

import com.fooda.api.core.application.port.out.*;
import com.fooda.api.core.domain.catalog.*;

import java.util.*;

public class InMemoryDishRepository implements DishRepository {

    private final Map<Long, Dish> rows = new LinkedHashMap<>();
    private long seq;

    @Override public List<Dish> findAll() { return List.copyOf(rows.values()); }

    @Override public Optional<Dish> findById(long id) { return Optional.ofNullable(rows.get(id)); }

    @Override
    public Dish save(Dish d) {
        long id = d.id() != null ? d.id() : ++seq;
        var saved = new Dish(id, d.name(), d.recipe(), d.version() == null ? 0L : d.version() + 1);
        rows.put(id, saved);
        return saved;
    }

    @Override public void softDelete(long id, long actorId) { rows.remove(id); }

    @Override public boolean isSupplyUsed(long supplyId) {
        return rows.values().stream().anyMatch(d -> d.recipe().stream().anyMatch(l -> l.supplyId() == supplyId));
    }
}
