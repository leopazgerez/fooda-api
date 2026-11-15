package com.fooda.api.catalog.infrastructure.out.persistence;

import com.fooda.api.shared.infrastructure.out.persistence.AuditedJpaEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Entity
@Table(name = "branch")
@SQLRestriction("deleted_at IS NULL")
class BranchJpaEntity extends AuditedJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "branch_id")
    Long id;

    @Column(name = "name", nullable = false, length = 120)
    String name;

    @Column(name = "address", nullable = false, length = 200)
    String address;

    @Column(name = "capacity", nullable = false)
    int capacity;

    @Column(name = "stock_threshold", nullable = false)
    int stockThreshold;

    @Column(name = "free_slots_threshold", nullable = false)
    int freeSlotsThreshold;

    @Column(name = "shipping_type", nullable = false, length = 20)
    String shippingType;

    @Column(name = "shipping_value", nullable = false, precision = 12, scale = 2)
    BigDecimal shippingValue;

    @Column(name = "affects_price", nullable = false)
    boolean affectsPrice;

    @Column(name = "is_kitchen", nullable = false)
    boolean kitchen;
}
