package com.fooda.api.production.fakes;

import com.fooda.api.core.application.port.out.PurchaseRepository;
import com.fooda.api.core.domain.production.Purchase;

import java.util.*;

public class InMemoryPurchaseRepository implements PurchaseRepository {

    private final Map<Long, Purchase> rows = new LinkedHashMap<>();
    private long seq;

    @Override public List<Purchase> findAll() { return List.copyOf(rows.values()); }

    @Override
    public List<Purchase> findByProduction(long productionId) {
        return rows.values().stream().filter(p -> p.productionId() != null && p.productionId() == productionId).toList();
    }

    @Override public Optional<Purchase> findById(long id) { return Optional.ofNullable(rows.get(id)); }

    @Override
    public Purchase save(Purchase p) {
        if (p.id() != null && p.version() == null) {
            throw new IllegalArgumentException("Saving an existing purchase needs its version");
        }
        long id = p.id() != null ? p.id() : ++seq;
        var saved = new Purchase(id, p.productionId(), p.supplierId(), p.status(), p.date(), p.lines(), p.total(),
                p.version() == null ? 0L : p.version() + 1);
        rows.put(id, saved);
        return saved;
    }

    @Override public void softDelete(long id, long actorId) { rows.remove(id); }

    @Override
    public boolean isSupplyUsed(long supplyId) {
        return rows.values().stream().flatMap(p -> p.lines().stream()).anyMatch(l -> l.supplyId() == supplyId);
    }

    @Override public boolean isSupplierUsed(long supplierId) {
        return rows.values().stream().anyMatch(p -> p.supplierId() == supplierId);
    }
}
