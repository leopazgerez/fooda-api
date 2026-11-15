package com.fooda.api.catalog.infrastructure.out.persistence;

import com.fooda.api.shared.infrastructure.out.persistence.AuditedJpaEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Entity
@Table(name = "reserve_fund")
@SQLRestriction("deleted_at IS NULL")
class ReserveFundJpaEntity extends AuditedJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reserve_fund_id")
    Long id;

    @Column(name = "name", nullable = false, length = 120)
    String name;

    @Column(name = "percentage", nullable = false, precision = 5, scale = 2)
    BigDecimal percentage;
}
