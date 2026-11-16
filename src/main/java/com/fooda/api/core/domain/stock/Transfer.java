package com.fooda.api.core.domain.stock;

import com.fooda.api.shared.domain.ValidationException;

import java.time.LocalDate;
import java.util.List;

public record Transfer(Long id, LocalDate date, long originBranchId, long destinationBranchId, String notes,
                       Long sourceProductionId, List<TransferLine> lines) {

    public Transfer {
        if (originBranchId == destinationBranchId) {
            throw new ValidationException("destinationBranchId", "Origen y destino deben ser distintos");
        }
        lines = lines == null ? List.of() : List.copyOf(lines);
    }
}
