package com.fooda.api.core.application.port.out;

import com.fooda.api.core.domain.stock.StockMovement;

import java.util.List;
import java.util.Optional;

public interface StockMovementRepository {

    StockMovement add(StockMovement m);

    List<StockMovement> findByProduction(long productionId);

    /** Live movements, by id. */
    List<StockMovement> findAll();

    Optional<StockMovement> findById(long id);

    /** Live TRANSFER_OUT/TRANSFER_IN movements with refId = transferId, by id. */
    List<StockMovement> findTransferLegs(long transferId);

    void softDelete(long movementId, long actorId);

    /** A live movement is of the pack. */
    boolean isPackUsed(long packId);

    /** A live movement is in the branch (every transfer leaves one per branch). */
    boolean isBranchUsed(long branchId);
}
