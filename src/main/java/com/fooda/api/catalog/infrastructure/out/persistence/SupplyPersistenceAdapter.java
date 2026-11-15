package com.fooda.api.catalog.infrastructure.out.persistence;

import com.fooda.api.core.application.port.out.SupplyRepository;
import com.fooda.api.core.domain.catalog.Composite;
import com.fooda.api.core.domain.catalog.CompositeRule;
import com.fooda.api.core.domain.catalog.PackagingType;
import com.fooda.api.core.domain.catalog.Supply;
import com.fooda.api.core.domain.catalog.SupplyPrice;
import com.fooda.api.core.domain.catalog.Unit;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class SupplyPersistenceAdapter implements SupplyRepository {

    private final SupplyJpaRepository supplies;
    private final SupplyPartJpaRepository parts;
    private final SupplyPriceJpaRepository prices;
    private final Clock clock;
    private final AuditorAware<Long> auditor;

    public SupplyPersistenceAdapter(SupplyJpaRepository supplies, SupplyPartJpaRepository parts,
                                    SupplyPriceJpaRepository prices, Clock clock, AuditorAware<Long> auditor) {
        this.supplies = supplies;
        this.parts = parts;
        this.prices = prices;
        this.clock = clock;
        this.auditor = auditor;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Supply> findAll() {
        Map<Long, List<Long>> partIds = new LinkedHashMap<>();
        parts.findAllByOrderById().forEach(p -> partIds.computeIfAbsent(p.supplyId, k -> new ArrayList<>()).add(p.partId));
        return supplies.findAll(Sort.by("name")).stream().map(e -> toDomain(e, partIds.getOrDefault(e.id, List.of()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Supply> findById(long id) {
        return supplies.findById(id).map(e -> toDomain(e, partIdsOf(id)));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByNameIgnoreCase(String name, Long exceptId) {
        return supplies.existsByName(name, exceptId);
    }

    @Override
    @Transactional
    public Supply save(Supply s) {
        SupplyJpaEntity saved = supplies.saveAndFlush(toEntity(s));
        List<Long> wanted = s.composite() == null ? List.of() : s.composite().partIds();
        Long actor = auditor.getCurrentAuditor()
                .orElseThrow(() -> new IllegalStateException("No hay usuario autenticado para auditar"));
        Set<Long> kept = new HashSet<>();
        for (SupplyPartJpaEntity p : parts.findBySupplyIdOrderById(saved.id)) {
            if (wanted.contains(p.partId)) {
                kept.add(p.partId);
            } else {
                parts.softDelete(p.id, actor, clock.instant());
            }
        }
        for (Long partId : wanted) {
            if (kept.add(partId)) {
                SupplyPartJpaEntity p = new SupplyPartJpaEntity();
                p.supplyId = saved.id;
                p.partId = partId;
                parts.saveAndFlush(p);
            }
        }
        return toDomain(saved, partIdsOf(saved.id));
    }

    @Override
    @Transactional
    public void softDelete(long id, long actorId) {
        parts.softDeleteBySupply(id, actorId, clock.instant());
        supplies.softDelete(id, actorId, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsBySupplier(long supplierId) {
        return supplies.existsBySupplierId(supplierId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPartOfComposite(long supplyId) {
        return parts.existsByPartId(supplyId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplyPrice> findAllPrices() {
        return prices.findAllByOrderByPriceDateAscIdAsc().stream().map(SupplyPersistenceAdapter::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SupplyPrice> findPrices(long supplyId) {
        return prices.findBySupplyIdOrderByPriceDateAscIdAsc(supplyId).stream().map(SupplyPersistenceAdapter::toDomain).toList();
    }

    @Override
    @Transactional
    public SupplyPrice addPrice(SupplyPrice p) {
        SupplyPriceJpaEntity e = new SupplyPriceJpaEntity();
        e.supplyId = p.supplyId();
        e.priceDate = p.date();
        e.packagePrice = p.packagePrice();
        return toDomain(prices.saveAndFlush(e));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SupplyPrice> findPrice(long priceId) {
        return prices.findById(priceId).map(SupplyPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public void softDeletePrice(long priceId, long actorId) {
        prices.softDelete(priceId, actorId, clock.instant());
    }

    private List<Long> partIdsOf(long supplyId) {
        return parts.findBySupplyIdOrderById(supplyId).stream().map(p -> p.partId).toList();
    }

    private static SupplyPrice toDomain(SupplyPriceJpaEntity e) {
        return new SupplyPrice(e.id, e.supplyId, e.priceDate, e.packagePrice);
    }

    private static Supply toDomain(SupplyJpaEntity e, List<Long> partIds) {
        Composite composite = e.compositeRule == null ? null
                : new Composite(CompositeRule.of(e.compositeRule, "composite"), partIds);
        return new Supply(e.id, e.name, Unit.of(e.unit, "unit"), e.supplierId, e.presentation, e.content,
                e.gramsPerUnit, e.packagingType == null ? null : PackagingType.of(e.packagingType, "packagingType"),
                composite, e.active, e.getVersion());
    }

    private static SupplyJpaEntity toEntity(Supply s) {
        SupplyJpaEntity e = new SupplyJpaEntity();
        e.id = s.id();
        e.name = s.name();
        e.unit = s.unit().code();
        e.supplierId = s.supplierId();
        e.presentation = s.presentation();
        e.content = s.content();
        e.gramsPerUnit = s.gramsPerUnit();
        e.packagingType = s.packagingType() == null ? null : s.packagingType().code();
        e.compositeRule = s.composite() == null ? null : s.composite().rule().code();
        e.active = s.active();
        e.setVersion(s.version());
        return e;
    }
}
