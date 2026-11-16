package com.fooda.api.stock.infrastructure.out.persistence;

import com.fooda.api.shared.infrastructure.out.persistence.AuditedJpaEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

@Entity
@Table(name = "stock_movement")
@SQLRestriction("deleted_at IS NULL")
class StockMovementJpaEntity extends AuditedJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stock_movement_id")
    Long id;

    @Column(name = "movement_date", nullable = false)
    LocalDate date;

    @Column(name = "branch_id", nullable = false)
    long branchId;

    @Column(name = "production_id", nullable = false)
    long productionId;

    @Column(name = "pack_id", nullable = false)
    long packId;

    @Column(name = "quantity", nullable = false)
    int quantity;

    @Column(name = "type", nullable = false, length = 20)
    String type;

    @Column(name = "ref_id")
    Long refId;

    @Column(name = "reason", length = 200)
    String reason;
}
