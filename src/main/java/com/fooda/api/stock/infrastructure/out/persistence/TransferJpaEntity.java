package com.fooda.api.stock.infrastructure.out.persistence;

import com.fooda.api.shared.infrastructure.out.persistence.AuditedJpaEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

@Entity
@Table(name = "transfer")
@SQLRestriction("deleted_at IS NULL")
class TransferJpaEntity extends AuditedJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transfer_id")
    Long id;

    @Column(name = "transfer_date", nullable = false)
    LocalDate date;

    @Column(name = "origin_branch_id", nullable = false)
    long originBranchId;

    @Column(name = "destination_branch_id", nullable = false)
    long destinationBranchId;

    @Column(name = "notes", length = 500)
    String notes;

    @Column(name = "source_production_id")
    Long sourceProductionId;
}
