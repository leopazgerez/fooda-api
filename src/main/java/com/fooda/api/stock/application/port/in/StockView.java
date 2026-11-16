package com.fooda.api.stock.application.port.in;

import com.fooda.api.core.domain.stock.BranchAlert;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record StockView(List<Row> rows, List<BranchTotal> byBranch) {

    public record Row(long branchId, String branch, long productionId, String lot, LocalDate lotDate, long packId,
                      String pack, int quantity, BigDecimal price) {
    }

    public record BranchTotal(long branchId, String branch, int total, int capacity, int freeSlots,
                              List<BranchAlert> alerts) {
    }
}
