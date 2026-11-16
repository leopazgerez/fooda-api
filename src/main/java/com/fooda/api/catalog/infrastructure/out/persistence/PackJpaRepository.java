package com.fooda.api.catalog.infrastructure.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

interface PackJpaRepository extends JpaRepository<PackJpaEntity, Long> {

    // native: bypasses @SQLRestriction so a deleted pack's number is never reused
    @Query(value = "select coalesce(max(number), 0) from pack", nativeQuery = true)
    int maxNumber();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PackJpaEntity e set e.deletedAt = :now, e.deletedBy = :by where e.id = :id and e.deletedAt is null")
    void softDelete(@Param("id") long id, @Param("by") long by, @Param("now") Instant now);
}
