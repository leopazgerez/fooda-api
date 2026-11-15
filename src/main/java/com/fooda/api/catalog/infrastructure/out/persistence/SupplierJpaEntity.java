package com.fooda.api.catalog.infrastructure.out.persistence;

import com.fooda.api.shared.infrastructure.out.persistence.AuditedJpaEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "supplier")
@SQLRestriction("deleted_at IS NULL")
class SupplierJpaEntity extends AuditedJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "supplier_id")
    Long id;

    @Column(name = "name", nullable = false, length = 120)
    String name;

    @Column(name = "purchase_day", length = 40)
    String purchaseDay;

    @Column(name = "sort_order", nullable = false)
    int sortOrder;
}
