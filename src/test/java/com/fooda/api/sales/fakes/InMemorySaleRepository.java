package com.fooda.api.sales.fakes;

import com.fooda.api.core.application.port.out.SaleCriteria;
import com.fooda.api.core.application.port.out.SaleRepository;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.Listing;
import com.fooda.api.shared.application.Page;
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

    @Override public List<Sale> findBetween(java.time.LocalDate from, java.time.LocalDate to) {
        return rows.values().stream().filter(s -> !s.date().isBefore(from) && !s.date().isAfter(to)).toList();
    }

    @Override public List<Sale> findWithCustomer() {
        return rows.values().stream().filter(s -> s.customerId() != null).toList();
    }

    @Override public Set<java.time.YearMonth> months() {
        return rows.values().stream().map(s -> java.time.YearMonth.from(s.date())).collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public Page<Sale> page(SaleCriteria c, ListQuery q) {
        var by = Comparator.comparingLong(Sale::id);
        var all = rows.values().stream()
                .filter(s -> (c.branchId() == null || c.branchId() == s.branchId())
                        && (c.type() == null || c.type() == s.type())
                        && (c.customerId() == null || c.customerId().equals(s.customerId()))
                        && (c.from() == null || !s.date().isBefore(c.from()))
                        && (c.to() == null || !s.date().isAfter(c.to()))).toList();
        return Listing.page(all, q, Map.of("date", Comparator.comparing(Sale::date), "id", by), "date", "desc", by);
    }

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
