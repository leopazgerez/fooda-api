package com.fooda.api.sales.infrastructure.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

interface PaymentJpaRepository extends JpaRepository<PaymentJpaEntity, Long> {

    List<PaymentJpaEntity> findBySaleIdOrderById(long saleId);

    List<PaymentJpaEntity> findAllByOrderById();

    List<PaymentJpaEntity> findByDateBetweenOrderById(LocalDate from, LocalDate to);

    @Query("select distinct e.date from PaymentJpaEntity e")
    List<LocalDate> dates();

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update PaymentJpaEntity e set e.deletedAt = :now, e.deletedBy = :by where e.saleId = :id and e.deletedAt is null")
    void softDeleteBySale(@Param("id") long id, @Param("by") long by, @Param("now") Instant now);
}
