package com.fooda.api.catalog.infrastructure.out.persistence;

import com.fooda.api.shared.infrastructure.out.persistence.AuditedJpaEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDate;

@Entity
@Table(name = "pack_version")
@SQLRestriction("deleted_at IS NULL")
class PackVersionJpaEntity extends AuditedJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pack_version_id")
    Long id;

    @Column(name = "pack_id", nullable = false)
    long packId;

    @Column(name = "valid_from", nullable = false)
    LocalDate validFrom;
}
