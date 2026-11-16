package com.fooda.api.production.infrastructure.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

interface PurchaseLineJpaRepository extends JpaRepository<PurchaseLineJpaEntity, Long> {

    List<PurchaseLineJpaEntity> findByPurchaseIdOrderById(long purchaseId);

    List<PurchaseLineJpaEntity> findByPurchaseIdInOrderById(Collection<Long> purchaseIds);

    @Query("select count(l) > 0 from PurchaseLineJpaEntity l where l.supplyId = :supplyId and l.deletedAt is null "
            + "and exists (select 1 from PurchaseJpaEntity p where p.id = l.purchaseId and p.deletedAt is null)")
    boolean isSupplyUsed(@Param("supplyId") long supplyId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PurchaseLineJpaEntity e set e.deletedAt = :now, e.deletedBy = :by where e.purchaseId = :id and e.deletedAt is null")
    void softDeleteByPurchase(@Param("id") long id, @Param("by") long by, @Param("now") Instant now);
}
