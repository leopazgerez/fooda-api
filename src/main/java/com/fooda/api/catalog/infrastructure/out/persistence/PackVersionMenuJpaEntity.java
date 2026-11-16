package com.fooda.api.catalog.infrastructure.out.persistence;

import com.fooda.api.shared.infrastructure.out.persistence.AuditedJpaEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "pack_version_menu")
@SQLRestriction("deleted_at IS NULL")
class PackVersionMenuJpaEntity extends AuditedJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pack_version_menu_id")
    Long id;

    @Column(name = "pack_version_id", nullable = false)
    long packVersionId;

    @Column(name = "position", nullable = false)
    short position;

    @Column(name = "menu_number", nullable = false)
    int menuNumber;

    @Column(name = "dish_id", nullable = false)
    long dishId;
}
