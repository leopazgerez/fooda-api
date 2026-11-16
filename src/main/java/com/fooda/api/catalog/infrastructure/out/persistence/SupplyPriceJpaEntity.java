package com.fooda.api.catalog.infrastructure.out.persistence;

import com.fooda.api.shared.infrastructure.out.persistence.AuditedJpaEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "supply_price")
@SQLRestriction("deleted_at IS NULL")
class SupplyPriceJpaEntity extends AuditedJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "supply_price_id")
    Long id;

    @Column(name = "supply_id", nullable = false)
    long supplyId;

    @Column(name = "price_date", nullable = false)
    LocalDate priceDate;

    @Column(name = "package_price", nullable = false, precision = 12, scale = 2)
    BigDecimal packagePrice;

    @Column(name = "purchase_id")
    Long purchaseId;
}
