package com.fooda.api.core.domain.production;

import com.fooda.api.shared.domain.ValidationException;

public record ProductionLine(long packId, int planned, Integer actual) {

    public ProductionLine {
        if (planned < 0) {
            throw new ValidationException("lines", "Las cantidades deben ser enteros ≥ 0");
        }
        if (actual != null && actual < 0) {
            throw new ValidationException("lines", "Las cantidades reales deben ser enteros ≥ 0");
        }
    }
}
