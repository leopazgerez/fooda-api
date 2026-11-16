package com.fooda.api.stock.application.port.in;

public interface StockUseCase {

    /** Rows filtered by each non-null argument; branch totals always cover all stock. */
    StockView view(Long branchId, Long packId, Long productionId);
}
