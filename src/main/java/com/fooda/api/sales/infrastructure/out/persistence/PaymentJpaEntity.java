package com.fooda.api.sales.infrastructure.out.persistence;

import com.fooda.api.shared.infrastructure.out.persistence.AuditedJpaEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "payment")
@SQLRestriction("deleted_at IS NULL")
class PaymentJpaEntity extends AuditedJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    Long id;

    @Column(name = "sale_id", nullable = false)
    long saleId;

    @Column(name = "payment_date", nullable = false)
    LocalDate date;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    BigDecimal amount;

    @Column(name = "method", nullable = false, length = 20)
    String method;
}
