package com.fooda.api.core.domain.sales;

import com.fooda.api.core.domain.stock.StockRules;
import com.fooda.api.shared.domain.ValidationException;

import java.math.BigDecimal;

public record SaleLine(long productionId, long packId, int quantity, BigDecimal unitPrice) {

    public SaleLine {
        if (quantity <= 0) {
            throw new ValidationException("lines", "Las cantidades deben ser enteros mayores a cero");
        }
        if (quantity > StockRules.MAX_QUANTITY) {
            throw new ValidationException("lines", "La cantidad es demasiado grande");
        }
        if (unitPrice == null || unitPrice.signum() < 0) {
            throw new ValidationException("lines", "Revisá los precios");
        }
    }
}
