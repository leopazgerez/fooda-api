package com.fooda.api.sales.fakes;

import com.fooda.api.core.application.port.out.SaleRepository;
import com.fooda.api.core.application.port.out.SoldPacks;
import com.fooda.api.core.domain.sales.Sale;
import com.fooda.api.core.domain.sales.SaleRules;

import java.util.*;

public class InMemorySaleRepository implements SaleRepository, SoldPacks {

    private final Map<Long, Sale> rows = new LinkedHashMap<>();
    private long seq;

    @Override
    public Sale add(Sale s) {
        Sale saved = new Sale(++seq, s.date(), s.branchId(), s.customerId(), s.type(), s.reason(), s.method(),
                s.discount(), s.shipping(), s.notes(), s.lines());
        rows.put(saved.id(), saved);
        return saved;
    }

    @Override public List<Sale> findAll() { return List.copyOf(rows.values()); }

    @Override public Optional<Sale> findById(long id) { return Optional.ofNullable(rows.get(id)); }

    @Override
    public boolean existsByCustomer(long customerId) {
        return rows.values().stream().anyMatch(s -> s.customerId() != null && s.customerId() == customerId);
    }

    @Override public void softDelete(long id, long actorId) { rows.remove(id); }

    @Override
    public Map<Long, Integer> soldByProduction() {
        Map<Long, Integer> out = new HashMap<>();
        rows.values().stream().filter(s -> SaleRules.isIncome(s.type()))
                .forEach(s -> s.lines().forEach(l -> out.merge(l.productionId(), l.quantity(), Integer::sum)));
        return out;
    }
}
