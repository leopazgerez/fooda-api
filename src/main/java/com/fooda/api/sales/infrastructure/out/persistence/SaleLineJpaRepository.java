package com.fooda.api.sales.infrastructure.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

interface SaleLineJpaRepository extends JpaRepository<SaleLineJpaEntity, Long> {

    List<SaleLineJpaEntity> findBySaleIdInOrderById(Collection<Long> saleIds);

    /** Live lines of live income sales (retail, wholesale, consignment), summed per production. */
    @Query("select l.productionId, sum(l.quantity) from SaleLineJpaEntity l where l.saleId in "
            + "(select s.id from SaleJpaEntity s where s.type in :types) "
            + "group by l.productionId")
    List<Object[]> soldByProduction(@Param("types") Collection<String> types);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SaleLineJpaEntity e set e.deletedAt = :now, e.deletedBy = :by where e.saleId = :id and e.deletedAt is null")
    void softDeleteBySale(@Param("id") long id, @Param("by") long by, @Param("now") Instant now);
}
