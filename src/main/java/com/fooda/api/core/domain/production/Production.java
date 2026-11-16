package com.fooda.api.core.domain.production;

import com.fooda.api.shared.domain.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record Production(Long id, String code, int number, ProductionStatus status, LocalDate start,
                         LocalDate closedOn, List<ProductionLine> lines, ProductionSnapshot snapshot,
                         Map<Long, BigDecimal> paidCosts, Map<Long, BigDecimal> leftovers, Long version) {

    public Production {
        if ((status == ProductionStatus.CLOSED) != (closedOn != null)) {
            throw new ValidationException("closedOn", "closedOn debe estar solo en producciones cerradas");
        }
        lines = lines == null ? List.of() : List.copyOf(lines);
        paidCosts = paidCosts == null ? Map.of() : Map.copyOf(paidCosts);
        leftovers = leftovers == null ? Map.of() : Map.copyOf(leftovers);
    }

    /** The INICIAL lot (number 0) that holds stock loaded without a production. */
    public boolean isInitial() {
        return number == 0;
    }

    public boolean isPlanned() {
        return status == ProductionStatus.PLANNED;
    }

    public Optional<SnapshotPack> snapshotPack(long packId) {
        if (snapshot == null) {
            return Optional.empty();
        }
        return snapshot.packs().stream().filter(p -> p.packId() == packId).findFirst();
    }

    public int actualPacks() {
        return lines.stream().mapToInt(l -> l.actual() == null ? 0 : l.actual()).sum();
    }

    public int packs() {
        return lines.stream().mapToInt(l -> l.actual() != null ? l.actual() : l.planned()).sum();
    }
}
