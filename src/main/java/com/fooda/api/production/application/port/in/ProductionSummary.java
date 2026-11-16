package com.fooda.api.production.application.port.in;

import com.fooda.api.core.domain.finance.AppliedOverhead;
import com.fooda.api.core.domain.finance.Payback;
import com.fooda.api.core.domain.production.ProductionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Port de produccionResumen (vistas.ts). */
public record ProductionSummary(long id, String code, int number, ProductionStatus status, LocalDate start,
                                LocalDate closedOn, int packs, BigDecimal estimatedCost, BigDecimal actualCost,
                                int sold, BigDecimal soldRatio, AppliedOverhead overhead, List<PackLine> packLines,
                                Payback payback, Long version) {

    public ProductionSummary {
        packLines = List.copyOf(packLines);
    }

    public record PackLine(long packId, String name, int planned, Integer actual, Long versionId,
                           BigDecimal directEstimated, BigDecimal directActual, BigDecimal costPerPack) {
    }
}
