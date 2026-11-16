package com.fooda.api.core.domain.stock;

import com.fooda.api.shared.domain.ValidationException;

public record TransferLine(long productionId, long packId, int quantity) {

    public TransferLine {
        if (quantity <= 0) {
            throw new ValidationException("lines", "Las cantidades deben ser enteros mayores a cero");
        }
    }
}
