package com.fooda.api.catalog.infrastructure.out.persistence;

import com.fooda.api.shared.infrastructure.out.persistence.AuditedJpaEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Entity
@Table(name = "recipe_line")
@SQLRestriction("deleted_at IS NULL")
class RecipeLineJpaEntity extends AuditedJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recipe_line_id")
    Long id;

    @Column(name = "dish_id", nullable = false)
    long dishId;

    @Column(name = "supply_id", nullable = false)
    long supplyId;

    @Column(name = "quantity", nullable = false, precision = 12, scale = 3)
    BigDecimal quantity;

    @Column(name = "unit", nullable = false, length = 2)
    String unit;

    @Column(name = "portions", nullable = false, precision = 8, scale = 3)
    BigDecimal portions;
}
