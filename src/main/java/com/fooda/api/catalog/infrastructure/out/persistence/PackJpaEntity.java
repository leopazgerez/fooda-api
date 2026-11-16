package com.fooda.api.catalog.infrastructure.out.persistence;

import com.fooda.api.shared.infrastructure.out.persistence.AuditedJpaEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Entity
@Table(name = "pack")
@SQLRestriction("deleted_at IS NULL")
class PackJpaEntity extends AuditedJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pack_id")
    Long id;

    @Column(name = "number", nullable = false)
    int number;

    @Column(name = "name", nullable = false, length = 120)
    String name;

    @Column(name = "active", nullable = false)
    boolean active;

    @Column(name = "price", precision = 12, scale = 2)
    BigDecimal price;
}
