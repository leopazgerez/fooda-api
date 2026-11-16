package com.fooda.api.sales.infrastructure.out.persistence;

import com.fooda.api.shared.infrastructure.out.persistence.AuditedJpaEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Entity
@Table(name = "sale_line")
@SQLRestriction("deleted_at IS NULL")
class SaleLineJpaEntity extends AuditedJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sale_line_id")
    Long id;

    @Column(name = "sale_id", nullable = false)
    long saleId;

    @Column(name = "production_id", nullable = false)
    long productionId;

    @Column(name = "pack_id", nullable = false)
    long packId;

    @Column(name = "quantity", nullable = false)
    int quantity;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    BigDecimal unitPrice;
}
