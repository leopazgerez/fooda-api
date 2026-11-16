package com.fooda.api.sales.infrastructure.out.persistence;

import com.fooda.api.shared.infrastructure.out.persistence.AuditedJpaEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "sale")
@SQLRestriction("deleted_at IS NULL")
class SaleJpaEntity extends AuditedJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sale_id")
    Long id;

    @Column(name = "sale_date", nullable = false)
    LocalDate date;

    @Column(name = "branch_id", nullable = false)
    long branchId;

    @Column(name = "customer_id")
    Long customerId;

    @Column(name = "type", nullable = false, length = 20)
    String type;

    @Column(name = "reason", length = 60)
    String reason;

    @Column(name = "payment_method", nullable = false, length = 20)
    String paymentMethod;

    @Column(name = "discount", nullable = false, precision = 12, scale = 2)
    BigDecimal discount;

    @Column(name = "shipping", nullable = false, precision = 12, scale = 2)
    BigDecimal shipping;

    @Column(name = "notes", length = 500)
    String notes;
}
