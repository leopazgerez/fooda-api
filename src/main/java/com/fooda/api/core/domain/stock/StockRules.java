package com.fooda.api.core.domain.stock;

import com.fooda.api.core.domain.catalog.Branch;

import java.util.ArrayList;
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

    public static int available(List<StockMovement> movements, long branchId, long productionId, long packId) {
        return clamp(availableLong(movements, branchId, productionId, packId));
    }

    private static long availableLong(List<StockMovement> movements, long branchId, long productionId, long packId) {
        return movements.stream()
                .filter(m -> m.branchId() == branchId && m.productionId() == productionId && m.packId() == packId)
                .mapToLong(StockMovement::quantity)
                .sum();
    }

    // sums run in long; values that don't fit an int saturate (the public records stay int)
    private static int clamp(long v) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, v));
    }

    /** Primer faltante (agrupando líneas repetidas por lote y pack) o vacío si todo alcanza. */
    public static Optional<Shortage> firstShortage(List<StockMovement> movements, long branchId, List<OutLine> lines) {
        Map<PackKey, Long> requested = new LinkedHashMap<>();
        lines.forEach(l -> requested.merge(new PackKey(l.productionId(), l.packId()), (long) l.quantity(), Long::sum));
        for (Map.Entry<PackKey, Long> e : requested.entrySet()) {
            long available = availableLong(movements, branchId, e.getKey().productionId(), e.getKey().packId());
            if (e.getValue() > available) {
                return Optional.of(new Shortage(e.getKey().productionId(), e.getKey().packId(), clamp(available),
                        clamp(e.getValue())));
            }
        }
        return Optional.empty();
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
