package com.fooda.api.stock.infrastructure.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

interface TransferJpaRepository extends JpaRepository<TransferJpaEntity, Long> {

    List<TransferJpaEntity> findAllByOrderById();

    List<TransferJpaEntity> findBySourceProductionIdOrderById(long productionId);

    @Query("select t from TransferJpaEntity t where t.id in "
            + "(select l.transferId from TransferLineJpaEntity l where l.productionId = :productionId and l.deletedAt is null) "
            + "order by t.id")
    List<TransferJpaEntity> findContainingProduction(@Param("productionId") long productionId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TransferJpaEntity e set e.deletedAt = :now, e.deletedBy = :by where e.id = :id and e.deletedAt is null")
    void softDelete(@Param("id") long id, @Param("by") long by, @Param("now") Instant now);
}
