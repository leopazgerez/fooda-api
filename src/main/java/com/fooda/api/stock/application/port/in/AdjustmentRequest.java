package com.fooda.api.stock.application.port.in;

import java.time.LocalDate;

public record AdjustmentRequest(LocalDate date, Long branchId, Long packId, Long productionId, Integer quantity,
                                String reason) {
}
