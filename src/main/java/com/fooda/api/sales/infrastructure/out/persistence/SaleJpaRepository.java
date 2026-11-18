package com.fooda.api.sales.infrastructure.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

interface SaleJpaRepository extends JpaRepository<SaleJpaEntity, Long>, JpaSpecificationExecutor<SaleJpaEntity> {

    List<SaleJpaEntity> findAllByOrderById();

    List<SaleJpaEntity> findByDateBetweenOrderById(LocalDate from, LocalDate to);

    List<SaleJpaEntity> findByCustomerIdNotNullOrderById();

    @Query("select distinct e.date from SaleJpaEntity e")
    List<LocalDate> dates();

    boolean existsByCustomerId(long customerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update SaleJpaEntity e set e.deletedAt = :now, e.deletedBy = :by where e.id = :id and e.deletedAt is null")
    void softDelete(@Param("id") long id, @Param("by") long by, @Param("now") Instant now);
}
