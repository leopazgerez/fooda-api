package com.fooda.api.catalog.fakes;

import com.fooda.api.core.application.port.out.*;
import com.fooda.api.core.domain.catalog.*;

import java.util.*;

public class InMemorySupplyRepository implements SupplyRepository {

    private final Map<Long, Supply> rows = new LinkedHashMap<>();
    private final Map<Long, SupplyPrice> prices = new LinkedHashMap<>();
    private long seq;
    private long priceSeq;
    private final Map<Long, Long> purchaseOfPrice = new HashMap<>();

    @Override public List<Supply> findAll() { return List.copyOf(rows.values()); }

    @Override public Optional<Supply> findById(long id) { return Optional.ofNullable(rows.get(id)); }

    @Override
    public boolean existsByNameIgnoreCase(String name, Long exceptId) {
        return rows.values().stream().anyMatch(s -> s.name().equalsIgnoreCase(name) && !s.id().equals(exceptId));
    }

    @Override
    public Supply save(Supply s) {
        long id = s.id() != null ? s.id() : ++seq;
        var saved = new Supply(id, s.name(), s.unit(), s.supplierId(), s.presentation(), s.content(), s.gramsPerUnit(),
                s.packagingType(), s.composite(), s.active(), s.version() == null ? 0L : s.version() + 1);
        rows.put(id, saved);
        return saved;
    }

    @Override
    public void softDelete(long id, long actorId) {
        rows.remove(id);
        prices.values().removeIf(p -> p.supplyId() == id);
    }

    @Override public boolean existsBySupplier(long supplierId) {
        return rows.values().stream().anyMatch(s -> s.supplierId() == supplierId);
    }

    @Override public boolean isPartOfComposite(long supplyId) {
        return rows.values().stream().anyMatch(s -> s.composite() != null && s.composite().partIds().contains(supplyId));
    }

    @Override public List<SupplyPrice> findAllPrices() { return List.copyOf(prices.values()); }

    @Override
    public List<SupplyPrice> findPrices(long supplyId) {
        return prices.values().stream().filter(p -> p.supplyId() == supplyId)
                .sorted(Comparator.comparing(SupplyPrice::date).thenComparing(SupplyPrice::id)).toList();
    }

    @Override
    public SupplyPrice addPrice(SupplyPrice p) {
        var saved = new SupplyPrice(++priceSeq, p.supplyId(), p.date(), p.packagePrice());
        prices.put(saved.id(), saved);
        return saved;
    }

    @Override
    public SupplyPrice addPurchasePrice(SupplyPrice p, long purchaseId) {
        var saved = addPrice(p);
        purchaseOfPrice.put(saved.id(), purchaseId);
        return saved;
    }

    @Override
    public void softDeletePurchasePrices(long purchaseId, long actorId) {
        prices.keySet().removeIf(id -> Long.valueOf(purchaseId).equals(purchaseOfPrice.get(id)));
    }

    @Override public Optional<SupplyPrice> findPrice(long priceId) { return Optional.ofNullable(prices.get(priceId)); }

    @Override public void softDeletePrice(long priceId, long actorId) { prices.remove(priceId); }
}
