package com.fooda.api.production.infrastructure.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

interface ProductionLineJpaRepository extends JpaRepository<ProductionLineJpaEntity, Long> {

    List<ProductionLineJpaEntity> findByProductionIdOrderById(long productionId);

    List<ProductionLineJpaEntity> findAllByOrderById();

    List<ProductionLineJpaEntity> findByProductionIdInOrderById(java.util.Collection<Long> productionIds);

    @Query("select count(l) > 0 from ProductionLineJpaEntity l where l.packId = :packId and l.deletedAt is null "
            + "and exists (select 1 from ProductionJpaEntity p where p.id = l.productionId and p.deletedAt is null)")
    boolean isPackUsed(@Param("packId") long packId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ProductionLineJpaEntity e set e.deletedAt = :now, e.deletedBy = :by where e.productionId = :id and e.deletedAt is null")
    void softDeleteByProduction(@Param("id") long id, @Param("by") long by, @Param("now") Instant now);
}
