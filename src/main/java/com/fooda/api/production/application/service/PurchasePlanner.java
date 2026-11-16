package com.fooda.api.production.application.service;

import com.fooda.api.core.application.port.out.PurchaseRepository;
import com.fooda.api.core.application.port.out.SupplierRepository;
import com.fooda.api.core.application.port.out.SupplyRepository;
import com.fooda.api.core.domain.catalog.Supply;
import com.fooda.api.core.domain.production.Production;
import com.fooda.api.core.domain.production.ProductionRules;
import com.fooda.api.core.domain.production.Purchase;
import com.fooda.api.core.domain.production.PurchaseLine;
import com.fooda.api.core.domain.production.PurchaseStatus;
import com.fooda.api.core.domain.production.ShoppingGroup;

import java.math.RoundingMode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Port de regenerarCompras: one pending purchase per supplier of the shopping list; bought ones stay untouched. */
public class PurchasePlanner {

    private final PurchaseRepository purchases;
    private final SupplyRepository supplies;
    private final SupplierRepository suppliers;

    public PurchasePlanner(PurchaseRepository purchases, SupplyRepository supplies, SupplierRepository suppliers) {
        this.purchases = purchases;
        this.supplies = supplies;
        this.suppliers = suppliers;
    }

    /** Must run inside the caller's transaction. */
    public void regenerate(Production p, long actorId) {
        List<Purchase> existing = purchases.findByProduction(p.id());
        Set<Long> bought = existing.stream().filter(x -> x.status() == PurchaseStatus.BOUGHT)
                .map(Purchase::supplierId).collect(Collectors.toSet());
        // keeps each supplier's pending id: an open panel still points at it
        Map<Long, Purchase> pending = new HashMap<>();
        existing.stream().filter(x -> x.status() == PurchaseStatus.PENDING)
                .forEach(x -> pending.putIfAbsent(x.supplierId(), x));
        List<Supply> live = supplies.findAll();
        Map<Long, Supply> frozen = new HashMap<>();
        ProductionRules.suppliesOf(p, live).forEach(s -> frozen.putIfAbsent(s.id(), s));
        Set<Long> kept = new HashSet<>(); // purchase ids
        for (ShoppingGroup g : ProductionRules.shoppingList(p, live, suppliers.findAll())) {
            long supplierId = g.supplier().id();
            if (bought.contains(supplierId)) {
                continue;
            }
            // rounded to the column scales so an unchanged list doesn't look changed to the adapter
            List<PurchaseLine> lines = g.items().stream()
                    .filter(it -> it.quantity().setScale(3, RoundingMode.HALF_UP).signum() > 0)
                    .map(it -> new PurchaseLine(it.supplyId(), it.quantity().setScale(3, RoundingMode.HALF_UP),
                            it.unitCost().multiply(frozen.get(it.supplyId()).content())
                                    .setScale(2, RoundingMode.HALF_UP)))
                    .toList();
            if (lines.isEmpty()) {
                continue;
            }
            Purchase prev = pending.get(supplierId);
            kept.add(purchases.save(new Purchase(prev == null ? null : prev.id(), p.id(), supplierId,
                    PurchaseStatus.PENDING, null, lines, null, prev == null ? null : prev.version())).id());
        }
        existing.stream().filter(x -> x.status() == PurchaseStatus.PENDING && !kept.contains(x.id()))
                .forEach(x -> purchases.softDelete(x.id(), actorId));
    }
}
