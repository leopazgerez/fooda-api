package com.fooda.api.catalog.infrastructure.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

interface RecipeLineJpaRepository extends JpaRepository<RecipeLineJpaEntity, Long> {

    List<RecipeLineJpaEntity> findByDishIdOrderById(long dishId);

    List<RecipeLineJpaEntity> findAllByOrderById();

    @Query("select count(l) > 0 from RecipeLineJpaEntity l where l.supplyId = :supplyId "
            + "and exists (select 1 from DishJpaEntity d where d.id = l.dishId)")
    boolean existsLiveBySupply(@Param("supplyId") long supplyId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RecipeLineJpaEntity e set e.deletedAt = :now, e.deletedBy = :by where e.dishId = :id and e.deletedAt is null")
    void softDeleteByDish(@Param("id") long id, @Param("by") long by, @Param("now") Instant now);
}
