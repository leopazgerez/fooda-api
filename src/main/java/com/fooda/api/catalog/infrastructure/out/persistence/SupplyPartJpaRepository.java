package com.fooda.api.catalog.infrastructure.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

interface SupplyPartJpaRepository extends JpaRepository<SupplyPartJpaEntity, Long> {

    List<SupplyPartJpaEntity> findBySupplyIdOrderById(long supplyId);

    List<SupplyPartJpaEntity> findAllByOrderById();

    boolean existsByPartId(long partId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SupplyPartJpaEntity e set e.deletedAt = :now, e.deletedBy = :by where e.id = :id and e.deletedAt is null")
    void softDelete(@Param("id") long id, @Param("by") long by, @Param("now") Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SupplyPartJpaEntity e set e.deletedAt = :now, e.deletedBy = :by where e.supplyId = :supplyId and e.deletedAt is null")
    void softDeleteBySupply(@Param("supplyId") long supplyId, @Param("by") long by, @Param("now") Instant now);
}
