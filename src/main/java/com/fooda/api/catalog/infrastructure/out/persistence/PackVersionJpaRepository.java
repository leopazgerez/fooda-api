package com.fooda.api.catalog.infrastructure.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

interface PackVersionJpaRepository extends JpaRepository<PackVersionJpaEntity, Long> {

    @Query("select v from PackVersionJpaEntity v where v.packId = :packId order by v.validFrom, v.id")
    List<PackVersionJpaEntity> findByPack(@Param("packId") long packId);

    @Query("select v from PackVersionJpaEntity v order by v.validFrom, v.id")
    List<PackVersionJpaEntity> findAllOrdered();

    @Query("select v.id from PackVersionJpaEntity v where v.packId = :packId")
    List<Long> findIdsByPack(@Param("packId") long packId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PackVersionJpaEntity e set e.deletedAt = :now, e.deletedBy = :by where e.packId = :id and e.deletedAt is null")
    void softDeleteByPack(@Param("id") long id, @Param("by") long by, @Param("now") Instant now);
}
