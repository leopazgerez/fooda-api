package com.fooda.api.core.application.port.out;

import com.fooda.api.core.domain.stock.StockMovement;

import java.util.List;

public interface StockMovementRepository {

    StockMovement add(StockMovement m);

    List<StockMovement> findByProduction(long productionId);

    void softDelete(long movementId, long actorId);

    /** A live movement is of the pack. */
    boolean isPackUsed(long packId);

    /** A live movement is in the branch (every transfer leaves one per branch). */
    boolean isBranchUsed(long branchId);
}
