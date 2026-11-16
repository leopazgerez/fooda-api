package com.fooda.api.core.domain.stock;

public record StockRow(long branchId, long productionId, long packId, int quantity) {
}
