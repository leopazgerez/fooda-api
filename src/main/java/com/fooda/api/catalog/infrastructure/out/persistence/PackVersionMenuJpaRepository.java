package com.fooda.api.catalog.infrastructure.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

interface PackVersionMenuJpaRepository extends JpaRepository<PackVersionMenuJpaEntity, Long> {

    List<PackVersionMenuJpaEntity> findByPackVersionIdOrderByPosition(long packVersionId);

    List<PackVersionMenuJpaEntity> findAllByOrderByPackVersionIdAscPositionAsc();

    @Query("select count(m) > 0 from PackVersionMenuJpaEntity m where m.dishId = :dishId and exists "
            + "(select 1 from PackVersionJpaEntity v, PackJpaEntity p where v.id = m.packVersionId and p.id = v.packId)")
    boolean existsLiveByDish(@Param("dishId") long dishId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PackVersionMenuJpaEntity e set e.deletedAt = :now, e.deletedBy = :by where e.packVersionId in :ids and e.deletedAt is null")
    void softDeleteByVersions(@Param("ids") List<Long> ids, @Param("by") long by, @Param("now") Instant now);
}
