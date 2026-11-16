package com.fooda.api.production.application.service;

import com.fooda.api.core.domain.catalog.Costs;
import com.fooda.api.core.domain.catalog.Supply;
import com.fooda.api.core.domain.production.Production;
import com.fooda.api.core.domain.production.Purchase;
import com.fooda.api.core.domain.production.PurchaseLine;
import com.fooda.api.shared.domain.Decimals;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Paid unit cost per supply of a production (the paid part of comprar). */
public final class PaidCosts {

    private PaidCosts() {
    }

    /** paid[supply] = packagePrice ÷ content; composites with some paid part are recombined (paid ?? snapshot ?? 0). */
    /** {@code supplies}: live ones overridden by the production's frozen ones, so every bought line has its content. */
    public static Map<Long, BigDecimal> apply(Map<Long, BigDecimal> current, Production p,
                                              List<Supply> supplies, List<PurchaseLine> lines) {
        Map<Long, BigDecimal> paid = new HashMap<>(current);
        for (PurchaseLine l : lines) {
            // content 1 only for a supply missing from the list (lines are validated against live supplies)
            BigDecimal content = supplies.stream().filter(s -> s.id() == l.supplyId()).findFirst()
                    .map(Supply::content).orElse(BigDecimal.ONE);
            paid.put(l.supplyId(), l.packagePrice().divide(content, Decimals.MC));
        }
        Map<Long, BigDecimal> snapshot = p.snapshot() == null ? Map.of() : p.snapshot().costs();
        for (Supply s : supplies) {
            if (s.composite() == null || s.composite().partIds().stream().noneMatch(paid::containsKey)) {
                continue;
            }
            List<BigDecimal> parts = s.composite().partIds().stream()
                    .map(id -> paid.getOrDefault(id, snapshot.getOrDefault(id, BigDecimal.ZERO))).toList();
            paid.put(s.id(), Costs.combine(s.composite().rule(), parts));
        }
        return paid;
    }

    /** {@link #apply} accumulated from scratch over {@code bought}, in id order. */
    public static Map<Long, BigDecimal> recompute(Production p, List<Supply> supplies, List<Purchase> bought) {
        Map<Long, BigDecimal> paid = Map.of();
        for (Purchase x : bought.stream().sorted(Comparator.comparing(Purchase::id)).toList()) {
            paid = apply(paid, p, supplies, x.lines());
        }
        return paid;
    }
}
