package com.fooda.api.sales.infrastructure.out.persistence;

import com.fooda.api.shared.infrastructure.out.persistence.AuditedJpaEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "expense")
@SQLRestriction("deleted_at IS NULL")
class ExpenseJpaEntity extends AuditedJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "expense_id")
    Long id;

    @Column(name = "expense_date", nullable = false)
    LocalDate date;

    @Column(name = "type", nullable = false, length = 60)
    String type;

    @Column(name = "detail", nullable = false, length = 200)
    String detail;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    BigDecimal amount;

    @Column(name = "amount_packs", precision = 12, scale = 2)
    BigDecimal amountPacks;

    @Column(name = "pack_price", precision = 12, scale = 2)
    BigDecimal packPrice;

    @Column(name = "method", nullable = false, length = 20)
    String method;

    @Column(name = "paid", nullable = false)
    boolean paid;

    @Column(name = "notes", length = 500)
    String notes;

    @Column(name = "purchase_id")
    Long purchaseId;

    @Column(name = "sale_id")
    Long saleId;
}
