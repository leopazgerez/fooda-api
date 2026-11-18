package com.fooda.api.production.fakes;

import com.fooda.api.core.application.port.out.StockMovementRepository;
import com.fooda.api.core.domain.stock.MovementType;
import com.fooda.api.core.domain.stock.StockMovement;

import java.util.*;

public class InMemoryStockMovementRepository implements StockMovementRepository {

    private final Map<Long, StockMovement> rows = new LinkedHashMap<>();
    private long seq;

    @Override
    public StockMovement add(StockMovement m) {
        var saved = new StockMovement(++seq, m.date(), m.branchId(), m.productionId(), m.packId(), m.quantity(),
                m.type(), m.refId(), m.reason());
        rows.put(saved.id(), saved);
        return saved;
    }

    @Override
    public List<StockMovement> findByProduction(long productionId) {
        return rows.values().stream().filter(m -> m.productionId() == productionId).toList();
    }

    @Override public List<StockMovement> findAll() { return List.copyOf(rows.values()); }

    @Override
    public List<StockMovement> findByBranchAndProductions(long branchId, Collection<Long> productionIds) {
        return rows.values().stream().filter(m -> m.branchId() == branchId
                && productionIds.contains(m.productionId())).toList();
    }

    @Override public java.util.List<com.fooda.api.core.domain.stock.StockRow> currentStock() {
        return com.fooda.api.core.domain.stock.StockRules.current(findAll());
    }

    @Override public Optional<StockMovement> findById(long id) { return Optional.ofNullable(rows.get(id)); }

    @Override
    public List<StockMovement> findTransferLegs(long transferId) {
        return rows.values().stream().filter(m -> m.refId() != null && m.refId() == transferId
                && (m.type() == MovementType.TRANSFER_OUT || m.type() == MovementType.TRANSFER_IN)).toList();
    }

    @Override
    public List<StockMovement> findBySale(long saleId) {
        return rows.values().stream().filter(m -> m.refId() != null && m.refId() == saleId
                && m.type() == MovementType.SALE).toList();
    }

    @Override public void softDelete(long movementId, long actorId) { rows.remove(movementId); }

    @Override public boolean isPackUsed(long packId) {
        return rows.values().stream().anyMatch(m -> m.packId() == packId);
    }

    @Override public boolean isBranchUsed(long branchId) {
        return rows.values().stream().anyMatch(m -> m.branchId() == branchId);
    }

    public List<StockMovement> all() { return List.copyOf(rows.values()); }
}
