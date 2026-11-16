package com.fooda.api.stock.infrastructure.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

interface StockMovementJpaRepository extends JpaRepository<StockMovementJpaEntity, Long> {

    List<StockMovementJpaEntity> findByProductionIdOrderById(long productionId);

    List<StockMovementJpaEntity> findAllByOrderById();

    List<StockMovementJpaEntity> findByRefIdAndTypeInOrderById(long refId, List<String> types);

    boolean existsByPackId(long packId);

    boolean existsByBranchId(long branchId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update StockMovementJpaEntity e set e.deletedAt = :now, e.deletedBy = :by where e.id = :id and e.deletedAt is null")
    void softDelete(@Param("id") long id, @Param("by") long by, @Param("now") Instant now);
}
