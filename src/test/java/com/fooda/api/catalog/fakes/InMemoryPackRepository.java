package com.fooda.api.catalog.fakes;

import com.fooda.api.core.application.port.out.*;
import com.fooda.api.core.domain.catalog.*;

import java.util.*;

public class InMemoryPackRepository implements PackRepository {

    private final Map<Long, Pack> rows = new LinkedHashMap<>();
    private final Map<Long, PackVersion> versions = new LinkedHashMap<>();
    private long seq;
    private long versionSeq;

    @Override public List<Pack> findAll() { return List.copyOf(rows.values()); }

    @Override public List<Pack> findAllById(java.util.Collection<Long> ids) {
        return rows.values().stream().filter(p -> ids.contains(p.id())).toList();
    }

    @Override public Optional<Pack> findById(long id) { return Optional.ofNullable(rows.get(id)); }

    @Override
    public Pack save(Pack p) {
        long id = p.id() != null ? p.id() : ++seq;
        var saved = new Pack(id, p.number(), p.name(), p.active(), p.price(), p.version() == null ? 0L : p.version() + 1);
        rows.put(id, saved);
        return saved;
    }

    @Override public int maxNumber() { return rows.values().stream().mapToInt(Pack::number).max().orElse(0); }

    @Override
    public void softDelete(long id, long actorId) {
        rows.remove(id);
        versions.values().removeIf(v -> v.packId() == id);
    }

    @Override public List<PackVersion> findAllVersions() { return List.copyOf(versions.values()); }

    @Override
    public List<PackVersion> findVersions(long packId) {
        return versions.values().stream().filter(v -> v.packId() == packId).sorted(PackVersions.BY_VALIDITY).toList();
    }

    @Override
    public PackVersion addVersion(PackVersion v) {
        var saved = new PackVersion(++versionSeq, v.packId(), v.validFrom(), v.menus());
        versions.put(saved.id(), saved);
        return saved;
    }

    @Override public boolean isDishUsed(long dishId) {
        return versions.values().stream().anyMatch(v -> rows.containsKey(v.packId())
                && v.menus().stream().anyMatch(m -> m.dishId() == dishId));
    }
}
