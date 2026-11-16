package com.fooda.api.sales.application.port.in;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SaleRequest(LocalDate date, Long branchId, Long customerId, String type, String method, String reason,
                          BigDecimal discount, BigDecimal shipping, String notes, String expenseType,
                          List<LineRequest> lines) {

    public record LineRequest(Long productionId, Long packId, Integer quantity, BigDecimal unitPrice) {
    }
}
