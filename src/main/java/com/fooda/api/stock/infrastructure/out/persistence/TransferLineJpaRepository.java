package com.fooda.api.stock.infrastructure.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

interface TransferLineJpaRepository extends JpaRepository<TransferLineJpaEntity, Long> {

    List<TransferLineJpaEntity> findByTransferIdInOrderById(Collection<Long> transferIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TransferLineJpaEntity e set e.deletedAt = :now, e.deletedBy = :by where e.transferId = :id and e.deletedAt is null")
    void softDeleteByTransfer(@Param("id") long id, @Param("by") long by, @Param("now") Instant now);
}
