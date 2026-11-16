package com.fooda.api.production.application.port.in;

import com.fooda.api.core.domain.stock.Transfer;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ProductionDetail(ProductionSummary summary, Map<Long, BigDecimal> leftovers, List<Transfer> transfers) {
}
