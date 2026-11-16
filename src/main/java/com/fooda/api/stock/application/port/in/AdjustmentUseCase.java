package com.fooda.api.stock.application.port.in;

import com.fooda.api.core.domain.stock.StockMovement;

public interface AdjustmentUseCase {

    StockMovement create(AdjustmentRequest r);

    void annul(long id, long actorId);
}
