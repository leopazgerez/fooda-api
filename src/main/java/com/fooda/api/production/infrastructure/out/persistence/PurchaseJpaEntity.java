package com.fooda.api.production.infrastructure.out.persistence;

import com.fooda.api.shared.infrastructure.out.persistence.AuditedJpaEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "purchase")
@SQLRestriction("deleted_at IS NULL")
class PurchaseJpaEntity extends AuditedJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_id")
    Long id;

    @Column(name = "production_id")
    Long productionId;

    @Column(name = "supplier_id", nullable = false)
    long supplierId;

    @Column(name = "status", nullable = false, length = 10)
    String status;

    @Column(name = "purchase_date")
    LocalDate date;

    @Column(name = "total", precision = 12, scale = 2)
    BigDecimal total;
}
