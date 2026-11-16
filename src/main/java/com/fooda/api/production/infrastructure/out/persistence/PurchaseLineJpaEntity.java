package com.fooda.api.production.infrastructure.out.persistence;

import com.fooda.api.shared.infrastructure.out.persistence.AuditedJpaEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Entity
@Table(name = "purchase_line")
@SQLRestriction("deleted_at IS NULL")
class PurchaseLineJpaEntity extends AuditedJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "purchase_line_id")
    Long id;

    @Column(name = "purchase_id", nullable = false)
    long purchaseId;

    @Column(name = "supply_id", nullable = false)
    long supplyId;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 3)
    BigDecimal quantity;

    @Column(name = "package_price", nullable = false, precision = 12, scale = 2)
    BigDecimal packagePrice;
}
