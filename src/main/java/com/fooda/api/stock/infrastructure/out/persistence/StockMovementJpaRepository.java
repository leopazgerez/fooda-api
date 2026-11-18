package com.fooda.api.stock.infrastructure.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

interface StockMovementJpaRepository extends JpaRepository<StockMovementJpaEntity, Long> {

    List<StockMovementJpaEntity> findByProductionIdOrderById(long productionId);

    List<StockMovementJpaEntity> findAllByOrderById();

    List<StockMovementJpaEntity> findByBranchIdAndProductionIdInOrderById(long branchId, Collection<Long> productionIds);

    /** Native: rows are (branch_id, production_id, pack_id, sum); deleted rows are excluded by hand. */
    @Query(value = "select branch_id, production_id, pack_id, sum(quantity) from stock_movement where deleted_at is null "
            + "group by branch_id, production_id, pack_id having sum(quantity) <> 0 order by production_id, branch_id, pack_id",
            nativeQuery = true)
    List<Object[]> currentStock();

    List<StockMovementJpaEntity> findByRefIdAndTypeInOrderById(long refId, List<String> types);

    boolean existsByPackId(long packId);

    boolean existsByBranchId(long branchId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update StockMovementJpaEntity e set e.deletedAt = :now, e.deletedBy = :by where e.id = :id and e.deletedAt is null")
    void softDelete(@Param("id") long id, @Param("by") long by, @Param("now") Instant now);
}
