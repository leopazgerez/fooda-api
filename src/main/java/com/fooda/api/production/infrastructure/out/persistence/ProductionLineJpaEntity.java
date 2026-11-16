package com.fooda.api.production.infrastructure.out.persistence;

import com.fooda.api.shared.infrastructure.out.persistence.AuditedJpaEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "production_line")
@SQLRestriction("deleted_at IS NULL")
class ProductionLineJpaEntity extends AuditedJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "production_line_id")
    Long id;

    @Column(name = "production_id", nullable = false)
    long productionId;

    @Column(name = "pack_id", nullable = false)
    long packId;

    @Column(name = "planned", nullable = false)
    int planned;

    @Column(name = "actual")
    Integer actual;
}
