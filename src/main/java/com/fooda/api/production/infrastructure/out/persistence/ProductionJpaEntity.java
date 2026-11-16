package com.fooda.api.production.infrastructure.out.persistence;

import com.fooda.api.shared.infrastructure.out.persistence.AuditedJpaEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

@Entity
@Table(name = "production")
@SQLRestriction("deleted_at IS NULL")
class ProductionJpaEntity extends AuditedJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "production_id")
    Long id;

    @Column(name = "code", nullable = false, length = 20)
    String code;

    @Column(name = "number", nullable = false)
    int number;

    @Column(name = "status", nullable = false, length = 10)
    String status;

    @Column(name = "start_date", nullable = false)
    LocalDate startDate;

    @Column(name = "closed_on")
    LocalDate closedOn;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "snapshot", nullable = false)
    String snapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "paid_costs", nullable = false)
    String paidCosts;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "leftovers", nullable = false)
    String leftovers;
}
