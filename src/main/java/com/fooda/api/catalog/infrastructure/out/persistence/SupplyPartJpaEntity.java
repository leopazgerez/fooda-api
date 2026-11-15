package com.fooda.api.catalog.infrastructure.out.persistence;

import com.fooda.api.shared.infrastructure.out.persistence.AuditedJpaEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "supply_part")
@SQLRestriction("deleted_at IS NULL")
class SupplyPartJpaEntity extends AuditedJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "supply_part_id")
    Long id;

    @Column(name = "supply_id", nullable = false)
    long supplyId;

    @Column(name = "part_id", nullable = false)
    long partId;
}
