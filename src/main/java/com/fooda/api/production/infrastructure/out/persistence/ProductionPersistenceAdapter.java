package com.fooda.api.production.infrastructure.out.persistence;

import com.fooda.api.core.application.port.out.ProductionRepository;
import com.fooda.api.core.domain.production.Production;
import com.fooda.api.core.domain.production.ProductionLine;
import com.fooda.api.core.domain.production.ProductionSnapshot;
import com.fooda.api.core.domain.production.ProductionStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository // translates the force-increment's OptimisticLockException into Spring's (→ 409)
public class ProductionPersistenceAdapter implements ProductionRepository {

    private static final TypeReference<Map<Long, BigDecimal>> DECIMALS = new TypeReference<>() { };
    /** Fixed format for the JSONB columns, independent of spring.jackson.* (spec §5.7). */
    static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .build();

    private final ProductionJpaRepository productions;
    private final ProductionLineJpaRepository lines;
    private final Clock clock;
    private final AuditorAware<Long> auditor;
    private final EntityManager em;

    public ProductionPersistenceAdapter(ProductionJpaRepository productions, ProductionLineJpaRepository lines,
                                        Clock clock, AuditorAware<Long> auditor, EntityManager em) {
        this.productions = productions;
        this.lines = lines;
        this.clock = clock;
        this.auditor = auditor;
        this.em = em;
    }

    @Override
    @Transactional(readOnly = true)
    public int maxNumber(YearMonth month) {
        return productions.maxNumber(month.atDay(1), month.plusMonths(1).atDay(1));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Production> findAll() {
        Map<Long, List<ProductionLine>> byProduction = new LinkedHashMap<>();
        lines.findAllByOrderById().forEach(l -> byProduction.computeIfAbsent(l.productionId, k -> new ArrayList<>()).add(toDomain(l)));
        return productions.findAll(Sort.by("id")).stream()
                .map(e -> toDomain(e, byProduction.getOrDefault(e.id, List.of()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Production> findById(long id) {
        return productions.findById(id).map(e -> toDomain(e, linesOf(id)));
    }

    @Override
    @Transactional
    public Production save(Production p) {
        ProductionJpaEntity e = new ProductionJpaEntity();
        e.id = p.id();
        e.code = p.code();
        e.number = p.number();
        e.status = p.status().code();
        e.startDate = p.start();
        e.closedOn = p.closedOn();
        e.snapshot = JSON.writeValueAsString(SnapshotJson.of(p.snapshot()));
        e.paidCosts = JSON.writeValueAsString(p.paidCosts());
        e.leftovers = JSON.writeValueAsString(p.leftovers());
        e.setVersion(p.version());
        ProductionJpaEntity saved = productions.saveAndFlush(e);
        if (p.id() == null || !p.lines().equals(linesOf(saved.id))) {
            if (p.id() != null) {
                // the lines live outside the production row: bump its version so concurrent line edits conflict
                em.lock(saved, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
                lines.softDeleteByProduction(saved.id, actor(), clock.instant());
            }
            for (ProductionLine l : p.lines()) {
                ProductionLineJpaEntity r = new ProductionLineJpaEntity();
                r.productionId = saved.id;
                r.packId = l.packId();
                r.planned = l.planned();
                r.actual = l.actual();
                lines.saveAndFlush(r);
            }
        }
        return toDomain(saved, linesOf(saved.id));
    }

    @Override
    @Transactional
    public void softDelete(long id, long actorId) {
        lines.softDeleteByProduction(id, actorId, clock.instant());
        productions.softDelete(id, actorId, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isPackUsed(long packId) {
        return lines.isPackUsed(packId);
    }

    private long actor() {
        return auditor.getCurrentAuditor()
                .orElseThrow(() -> new IllegalStateException("No hay usuario autenticado para auditar"));
    }

    private List<ProductionLine> linesOf(long productionId) {
        return lines.findByProductionIdOrderById(productionId).stream().map(ProductionPersistenceAdapter::toDomain).toList();
    }

    private static ProductionLine toDomain(ProductionLineJpaEntity l) {
        return new ProductionLine(l.packId, l.planned, l.actual);
    }

    private Production toDomain(ProductionJpaEntity e, List<ProductionLine> ls) {
        return new Production(e.id, e.code, e.number, ProductionStatus.of(e.status, "status"), e.startDate, e.closedOn, ls,
                snapshot(e.snapshot), JSON.readValue(e.paidCosts, DECIMALS), JSON.readValue(e.leftovers, DECIMALS),
                e.getVersion());
    }

    /** Stored JSON → domain snapshot; package-visible so a test can pin the stored format. */
    static ProductionSnapshot snapshot(String json) {
        SnapshotJson s = JSON.readValue(json, SnapshotJson.class);
        return s == null ? null : s.toDomain();
    }
}
