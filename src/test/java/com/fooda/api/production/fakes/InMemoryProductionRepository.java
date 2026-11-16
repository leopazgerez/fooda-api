package com.fooda.api.production.fakes;

import com.fooda.api.core.application.port.out.ProductionRepository;
import com.fooda.api.core.domain.production.Production;

import java.time.YearMonth;
import java.util.*;

public class InMemoryProductionRepository implements ProductionRepository {

    private final Map<Long, Production> rows = new LinkedHashMap<>();
    private final List<Production> annulled = new ArrayList<>();
    private long seq;

    @Override public List<Production> findAll() { return List.copyOf(rows.values()); }

    @Override public Optional<Production> findById(long id) { return Optional.ofNullable(rows.get(id)); }

    @Override public Optional<Production> findInitial() {
        return rows.values().stream().filter(p -> p.number() == 0).findFirst();
    }

    @Override
    public Production save(Production p) {
        long id = p.id() != null ? p.id() : ++seq;
        var saved = new Production(id, p.code(), p.number(), p.status(), p.start(), p.closedOn(), p.lines(),
                p.snapshot(), p.paidCosts(), p.leftovers(), p.version() == null ? 0L : p.version() + 1);
        rows.put(id, saved);
        return saved;
    }

    @Override public void softDelete(long id, long actorId) { annulled.add(rows.remove(id)); }

    @Override public int maxNumber(YearMonth month) {
        return java.util.stream.Stream.concat(rows.values().stream(), annulled.stream())
                .filter(p -> YearMonth.from(p.start()).equals(month)).mapToInt(Production::number).max().orElse(0);
    }

    @Override public boolean isPackUsed(long packId) {
        return rows.values().stream().flatMap(p -> p.lines().stream()).anyMatch(l -> l.packId() == packId);
    }
}
