package com.fooda.api.stock.application.port.in;

import com.fooda.api.core.domain.stock.OutLine;

import java.time.LocalDate;
import java.util.List;

public record TransferRequest(LocalDate date, Long originBranchId, Long destinationBranchId, String notes,
                              List<OutLine> lines) {
}
