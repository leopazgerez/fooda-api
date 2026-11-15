package com.fooda.api.catalog.infrastructure.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

interface SupplyJpaRepository extends JpaRepository<SupplyJpaEntity, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SupplyJpaEntity e set e.deletedAt = :now, e.deletedBy = :by where e.id = :id and e.deletedAt is null")
    void softDelete(@Param("id") long id, @Param("by") long by, @Param("now") Instant now);

    @Query("select count(e) > 0 from SupplyJpaEntity e where lower(e.name) = lower(:name) and (:exceptId is null or e.id <> :exceptId)")
    boolean existsByName(@Param("name") String name, @Param("exceptId") Long exceptId);

    boolean existsBySupplierId(long supplierId);
}
