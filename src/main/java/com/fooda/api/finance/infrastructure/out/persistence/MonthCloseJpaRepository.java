package com.fooda.api.finance.infrastructure.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface MonthCloseJpaRepository extends JpaRepository<MonthCloseJpaEntity, Long> {

    Optional<MonthCloseJpaEntity> findByMonth(String month);

    List<MonthCloseJpaEntity> findAllByOrderByMonth();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update MonthCloseJpaEntity e set e.deletedAt = :now, e.deletedBy = :by where e.month = :month and e.deletedAt is null")
    void softDelete(@Param("month") String month, @Param("by") long by, @Param("now") Instant now);
}
