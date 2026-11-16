package com.fooda.api.production.infrastructure.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;

interface ProductionJpaRepository extends JpaRepository<ProductionJpaEntity, Long> {

    /** Native so @SQLRestriction does not hide annulled rows. */
    @Query(value = "select coalesce(max(number), 0) from production where start_date >= :from and start_date < :to",
            nativeQuery = true)
    int maxNumber(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ProductionJpaEntity e set e.deletedAt = :now, e.deletedBy = :by where e.id = :id and e.deletedAt is null")
    void softDelete(@Param("id") long id, @Param("by") long by, @Param("now") Instant now);
}
