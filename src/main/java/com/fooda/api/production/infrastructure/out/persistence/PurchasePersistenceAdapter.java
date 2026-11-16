package com.fooda.api.production.infrastructure.out.persistence;

import com.fooda.api.core.application.port.out.PurchaseRepository;
import com.fooda.api.core.domain.production.Purchase;
import com.fooda.api.core.domain.production.PurchaseLine;
import com.fooda.api.core.domain.production.PurchaseStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository // translates the force-increment's OptimisticLockException into Spring's (→ 409)
public class PurchasePersistenceAdapter implements PurchaseRepository {

    private final PurchaseJpaRepository purchases;
    private final PurchaseLineJpaRepository lines;
    private final Clock clock;
    private final AuditorAware<Long> auditor;
    private final EntityManager em;

    public PurchasePersistenceAdapter(PurchaseJpaRepository purchases, PurchaseLineJpaRepository lines, Clock clock,
                                      AuditorAware<Long> auditor, EntityManager em) {
        this.purchases = purchases;
        this.lines = lines;
        this.clock = clock;
        this.auditor = auditor;
        this.em = em;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Purchase> findAll() {
        return withLines(purchases.findAll(Sort.by("id")));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Purchase> findByProduction(long productionId) {
        return withLines(purchases.findByProductionIdOrderById(productionId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Purchase> findById(long id) {
        return purchases.findById(id).map(e -> toDomain(e, linesOf(id)));
    }

    @Override
    @Transactional
    public Purchase save(Purchase p) {
        PurchaseJpaEntity e = new PurchaseJpaEntity();
        e.id = p.id();
        e.productionId = p.productionId();
        e.supplierId = p.supplierId();
        e.status = p.status().code();
        e.date = p.date();
        e.total = p.total();
        e.setVersion(p.version());
        PurchaseJpaEntity saved = purchases.saveAndFlush(e);
        if (p.id() == null || !sameLines(linesOf(saved.id), p.lines())) {
            if (p.id() != null) {
                // the lines live outside the purchase row: bump its version so concurrent line edits conflict
                em.lock(saved, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
                Long actor = auditor.getCurrentAuditor()
                        .orElseThrow(() -> new IllegalStateException("No hay usuario autenticado para auditar"));
                lines.softDeleteByPurchase(saved.id, actor, clock.instant());
            }
            for (PurchaseLine l : p.lines()) {
                PurchaseLineJpaEntity r = new PurchaseLineJpaEntity();
                r.purchaseId = saved.id;
                r.supplyId = l.supplyId();
                r.quantity = l.quantity();
                r.packagePrice = l.packagePrice();
                lines.saveAndFlush(r);
            }
        }
        return toDomain(saved, linesOf(saved.id));
    }

    @Override
    @Transactional
    public void softDelete(long id, long actorId) {
        lines.softDeleteByPurchase(id, actorId, clock.instant());
        purchases.softDelete(id, actorId, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSupplyUsed(long supplyId) {
        return lines.isSupplyUsed(supplyId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSupplierUsed(long supplierId) {
        return purchases.existsBySupplierId(supplierId);
    }

    private List<Purchase> withLines(List<PurchaseJpaEntity> es) {
        if (es.isEmpty()) {
            return List.of();
        }
        Map<Long, List<PurchaseLine>> byPurchase = new LinkedHashMap<>();
        lines.findByPurchaseIdInOrderById(es.stream().map(e -> e.id).toList()).forEach(l -> byPurchase.computeIfAbsent(l.purchaseId, k -> new ArrayList<>()).add(toDomain(l)));
        return es.stream().map(e -> toDomain(e, byPurchase.getOrDefault(e.id, List.of()))).toList();
    }

    private List<PurchaseLine> linesOf(long purchaseId) {
        return lines.findByPurchaseIdOrderById(purchaseId).stream().map(PurchasePersistenceAdapter::toDomain).toList();
    }

    /** Field by field with compareTo: record equals is scale-sensitive and the DB returns column scale. */
    private static boolean sameLines(List<PurchaseLine> a, List<PurchaseLine> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            PurchaseLine x = a.get(i);
            PurchaseLine y = b.get(i);
            if (x.supplyId() != y.supplyId() || x.quantity().compareTo(y.quantity()) != 0
                    || x.packagePrice().compareTo(y.packagePrice()) != 0) {
                return false;
            }
        }
        return true;
    }

    private static PurchaseLine toDomain(PurchaseLineJpaEntity l) {
        return new PurchaseLine(l.supplyId, l.quantity, l.packagePrice);
    }

    private static Purchase toDomain(PurchaseJpaEntity e, List<PurchaseLine> ls) {
        return new Purchase(e.id, e.productionId, e.supplierId, PurchaseStatus.of(e.status, "status"), e.date, ls,
                e.total, e.getVersion());
    }
}
