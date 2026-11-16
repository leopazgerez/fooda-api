package com.fooda.api.core.domain.stock;

import com.fooda.api.shared.domain.ValidationException;

import java.time.LocalDate;

public record StockMovement(Long id, LocalDate date, long branchId, long productionId, long packId, int quantity,
                            MovementType type, Long refId, String reason) {

    public StockMovement {
        if (quantity == 0) {
            throw new ValidationException("quantity", "quantity no puede ser 0");
        }
    }
}
