package com.fooda.api.core.domain.production;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record Purchase(Long id, Long productionId, long supplierId, PurchaseStatus status, LocalDate date,
                       List<PurchaseLine> lines, BigDecimal total, Long version) {

    public Purchase {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
