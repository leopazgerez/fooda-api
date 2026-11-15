package com.fooda.api.catalog.infrastructure.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

interface BranchJpaRepository extends JpaRepository<BranchJpaEntity, Long> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update BranchJpaEntity e set e.deletedAt = :now, e.deletedBy = :by where e.id = :id and e.deletedAt is null")
    void softDelete(@Param("id") long id, @Param("by") long by, @Param("now") Instant now);

    Optional<BranchJpaEntity> findFirstByKitchenTrue();}
