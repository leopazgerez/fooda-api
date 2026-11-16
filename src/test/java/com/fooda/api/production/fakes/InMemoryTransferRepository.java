package com.fooda.api.production.fakes;

import com.fooda.api.core.application.port.out.TransferRepository;
import com.fooda.api.core.domain.stock.Transfer;

import java.util.*;

public class InMemoryTransferRepository implements TransferRepository {

    private final Map<Long, Transfer> rows = new LinkedHashMap<>();
    private long seq;

    @Override
    public Transfer add(Transfer t) {
        var saved = new Transfer(++seq, t.date(), t.originBranchId(), t.destinationBranchId(), t.notes(),
                t.sourceProductionId(), t.lines());
        rows.put(saved.id(), saved);
        return saved;
    }

    @Override
    public List<Transfer> findBySourceProduction(long productionId) {
        return rows.values().stream()
                .filter(t -> t.sourceProductionId() != null && t.sourceProductionId() == productionId).toList();
    }

    @Override
    public List<Transfer> findContainingProduction(long productionId) {
        return rows.values().stream()
                .filter(t -> t.lines().stream().anyMatch(l -> l.productionId() == productionId)).toList();
    }

    @Override public List<Transfer> findAll() { return List.copyOf(rows.values()); }

    @Override public Optional<Transfer> findById(long id) { return Optional.ofNullable(rows.get(id)); }

    @Override public void softDelete(long id, long actorId) { rows.remove(id); }
}
