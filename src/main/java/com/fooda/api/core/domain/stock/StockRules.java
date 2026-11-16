package com.fooda.api.core.domain.stock;

import com.fooda.api.core.domain.catalog.Branch;
import com.fooda.api.core.domain.production.Production;
import com.fooda.api.shared.domain.ValidationException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class StockRules {

    private record Key(long branchId, long productionId, long packId) {
    }

    private record PackKey(long productionId, long packId) {
    }

    /** Largest single movement quantity; keeps every stored sum far from int overflow. */
    public static final int MAX_QUANTITY = 100_000;

    /** Message for a {@link Shortage}: available, pack name, lot code, branch name. */
    public static final String SHORTAGE_MESSAGE = "Solo hay %d packs de %s (%s) en %s";

    private StockRules() {
    }

    /** Stock por sede x producción x pack; omite ceros; FIFO (producción más vieja primero). */
    public static List<StockRow> current(List<StockMovement> movements) {
        Map<Key, Long> sums = new LinkedHashMap<>();
        for (StockMovement m : movements) {
            sums.merge(new Key(m.branchId(), m.productionId(), m.packId()), (long) m.quantity(), Long::sum);
        }
        return sums.entrySet().stream()
                .filter(e -> e.getValue() != 0)
                .map(e -> new StockRow(e.getKey().branchId(), e.getKey().productionId(), e.getKey().packId(), clamp(e.getValue())))
                .sorted(Comparator.comparingLong(StockRow::productionId)
                        .thenComparingLong(StockRow::branchId)
                        .thenComparingLong(StockRow::packId))
                .toList();
    }

    /**
     * Packs that can leave on {@code day}: the lowest running balance from that day on. Movements dated later
     * still have to be covered, so a back-dated exit can't take packs that arrived afterwards.
     */
    public static int available(List<StockMovement> movements, long branchId, long productionId, long packId,
                                LocalDate day) {
        return clamp(availableLong(movements, branchId, productionId, packId, day));
    }

    private static long availableLong(List<StockMovement> movements, long branchId, long productionId, long packId,
                                      LocalDate day) {
        List<StockMovement> lot = movements.stream()
                .filter(m -> m.branchId() == branchId && m.productionId() == productionId && m.packId() == packId)
                .sorted(Comparator.comparing(StockMovement::date))
                .toList();
        long balance = lot.stream().filter(m -> !m.date().isAfter(day)).mapToLong(StockMovement::quantity).sum();
        long lowest = balance;
        for (StockMovement m : lot) {
            if (m.date().isAfter(day)) {
                balance += m.quantity();
                lowest = Math.min(lowest, balance);
            }
        }
        return lowest;
    }

    // sums run in long; values that don't fit an int saturate (the public records stay int)
    private static int clamp(long v) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, v));
    }

    /** Primer faltante para salidas en {@code day} (agrupando líneas repetidas por lote y pack), o vacío. */
    public static Optional<Shortage> firstShortage(List<StockMovement> movements, long branchId, List<OutLine> lines,
                                                   LocalDate day) {
        Map<PackKey, Long> requested = new LinkedHashMap<>();
        lines.forEach(l -> requested.merge(new PackKey(l.productionId(), l.packId()), (long) l.quantity(), Long::sum));
        for (Map.Entry<PackKey, Long> e : requested.entrySet()) {
            long available = availableLong(movements, branchId, e.getKey().productionId(), e.getKey().packId(), day);
            if (e.getValue() > available) {
                return Optional.of(new Shortage(e.getKey().productionId(), e.getKey().packId(), clamp(available),
                        clamp(e.getValue())));
            }
        }
        return Optional.empty();
    }

    /** A stock exit dated before the closing of one of its lots (the INICIAL lot has no real closing date). */
    public static void requireAfterLotsClosed(Collection<Production> lots, LocalDate day) {
        if (lots.stream().anyMatch(p -> !p.isInitial() && p.closedOn() != null && day.isBefore(p.closedOn()))) {
            throw new ValidationException("date", "La fecha no puede ser anterior al cierre del lote");
        }
    }

    public static List<BranchAlert> alerts(Branch branch, int total) {
        List<BranchAlert> out = new ArrayList<>();
        if (total <= branch.stockThreshold()) {
            out.add(BranchAlert.LOW_STOCK);
        }
        if (branch.capacity() - total >= branch.freeSlotsThreshold()) {
            out.add(BranchAlert.FREE_SLOTS);
        }
        return out;
    }
}
