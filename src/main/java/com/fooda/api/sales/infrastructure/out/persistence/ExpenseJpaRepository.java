package com.fooda.api.sales.infrastructure.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

interface ExpenseJpaRepository extends JpaRepository<ExpenseJpaEntity, Long> {

    Optional<ExpenseJpaEntity> findByPurchaseId(long purchaseId);

    Optional<ExpenseJpaEntity> findBySaleId(long saleId);

    List<ExpenseJpaEntity> findByDateBetweenOrderByDateDescIdDesc(LocalDate from, LocalDate to);

    @Query("select distinct e.date from ExpenseJpaEntity e")
    List<LocalDate> dates();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ExpenseJpaEntity e set e.deletedAt = :now, e.deletedBy = :by where e.id = :id and e.deletedAt is null")
    void softDelete(@Param("id") long id, @Param("by") long by, @Param("now") Instant now);
}
