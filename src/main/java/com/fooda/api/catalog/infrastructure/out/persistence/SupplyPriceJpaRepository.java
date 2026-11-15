package com.fooda.api.catalog.infrastructure.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

interface SupplyPriceJpaRepository extends JpaRepository<SupplyPriceJpaEntity, Long> {

    List<SupplyPriceJpaEntity> findBySupplyIdOrderByPriceDateAscIdAsc(long supplyId);

    List<SupplyPriceJpaEntity> findAllByOrderByPriceDateAscIdAsc();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SupplyPriceJpaEntity e set e.deletedAt = :now, e.deletedBy = :by where e.id = :id and e.deletedAt is null")
    void softDelete(@Param("id") long id, @Param("by") long by, @Param("now") Instant now);
}
