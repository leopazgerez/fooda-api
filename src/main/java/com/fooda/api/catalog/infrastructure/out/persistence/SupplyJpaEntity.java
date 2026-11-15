package com.fooda.api.catalog.infrastructure.out.persistence;

import com.fooda.api.shared.infrastructure.out.persistence.AuditedJpaEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Entity
@Table(name = "supply")
@SQLRestriction("deleted_at IS NULL")
class SupplyJpaEntity extends AuditedJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "supply_id")
    Long id;

    @Column(name = "name", nullable = false, length = 120)
    String name;

    @Column(name = "unit", nullable = false, length = 2)
    String unit;

    @Column(name = "supplier_id", nullable = false)
    long supplierId;

    @Column(name = "presentation", length = 60)
    String presentation;

    @Column(name = "content", nullable = false, precision = 12, scale = 3)
    BigDecimal content;

    @Column(name = "grams_per_unit", precision = 12, scale = 3)
    BigDecimal gramsPerUnit;

    @Column(name = "packaging_type", length = 10)
    String packagingType;

    @Column(name = "composite_rule", length = 10)
    String compositeRule;

    @Column(name = "active", nullable = false)
    boolean active;
}
