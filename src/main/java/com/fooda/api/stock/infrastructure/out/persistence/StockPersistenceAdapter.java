package com.fooda.api.stock.infrastructure.out.persistence;

import com.fooda.api.core.application.port.out.StockMovementRepository;
import com.fooda.api.core.domain.stock.MovementType;
import com.fooda.api.core.domain.stock.StockMovement;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

@Component
public class StockPersistenceAdapter implements StockMovementRepository {

    private final StockMovementJpaRepository movements;
    private final Clock clock;

    public StockPersistenceAdapter(StockMovementJpaRepository movements, Clock clock) {
        this.movements = movements;
        this.clock = clock;
    }

    @Override
    @Transactional
    public StockMovement add(StockMovement m) {
        StockMovementJpaEntity e = new StockMovementJpaEntity();
        e.date = m.date();
        e.branchId = m.branchId();
        e.productionId = m.productionId();
        e.packId = m.packId();
        e.quantity = m.quantity();
        e.type = m.type().code();
        e.refId = m.refId();
        e.reason = m.reason();
        return toDomain(movements.saveAndFlush(e));
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockMovement> findByProduction(long productionId) {
        return movements.findByProductionIdOrderById(productionId).stream().map(StockPersistenceAdapter::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockMovement> findAll() {
        return movements.findAllByOrderById().stream().map(StockPersistenceAdapter::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StockMovement> findById(long id) {
        return movements.findById(id).map(StockPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StockMovement> findTransferLegs(long transferId) {
        return movements.findByRefIdAndTypeInOrderById(transferId,
                        List.of(MovementType.TRANSFER_OUT.code(), MovementType.TRANSFER_IN.code()))
                .stream().map(StockPersistenceAdapter::toDomain).toList();
    }

    @Override
    @Transactional
    public void softDelete(long movementId, long actorId) {
        movements.softDelete(movementId, actorId, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPackUsed(long packId) {
        return movements.existsByPackId(packId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isBranchUsed(long branchId) {
        return movements.existsByBranchId(branchId);
    }

    private static StockMovement toDomain(StockMovementJpaEntity e) {
        return new StockMovement(e.id, e.date, e.branchId, e.productionId, e.packId, e.quantity,
                MovementType.of(e.type, "type"), e.refId, e.reason);
    }
}
