package com.fooda.api.stock.infrastructure.out.persistence;

import com.fooda.api.shared.infrastructure.out.persistence.AuditedJpaEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "transfer_line")
@SQLRestriction("deleted_at IS NULL")
class TransferLineJpaEntity extends AuditedJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "transfer_line_id")
    Long id;

    @Column(name = "transfer_id", nullable = false)
    long transferId;

    @Column(name = "production_id", nullable = false)
    long productionId;

    @Column(name = "pack_id", nullable = false)
    long packId;

    @Column(name = "quantity", nullable = false)
    int quantity;
}
