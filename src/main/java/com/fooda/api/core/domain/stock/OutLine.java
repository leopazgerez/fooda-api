package com.fooda.api.core.domain.stock;

import com.fooda.api.shared.domain.ValidationException;

/** A requested stock exit (transfer line, later a sale line). */
public record OutLine(long productionId, long packId, int quantity) {

    public OutLine {
        if (quantity <= 0) {
            throw new ValidationException("lines", "Las cantidades deben ser enteros mayores a cero");
        }
        if (quantity > StockRules.MAX_QUANTITY) {
            throw new ValidationException("lines", "La cantidad es demasiado grande");
        }
    }
}
