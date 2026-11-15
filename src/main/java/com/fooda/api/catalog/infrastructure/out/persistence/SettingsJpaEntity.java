package com.fooda.api.catalog.infrastructure.out.persistence;

import com.fooda.api.shared.infrastructure.out.persistence.AuditedJpaEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.List;

/** Single row: settings_id is always TRUE. */
@Entity
@Table(name = "settings")
@SQLRestriction("deleted_at IS NULL")
class SettingsJpaEntity extends AuditedJpaEntity {

    @Id
    @Column(name = "settings_id")
    Boolean id = Boolean.TRUE;

    @Column(name = "pack_price", nullable = false, precision = 12, scale = 2)
    BigDecimal packPrice;

    @Column(name = "oil_ml_per_pack", nullable = false, precision = 12, scale = 3)
    BigDecimal oilMlPerPack;

    @Column(name = "oil_supply_id")
    Long oilSupplyId;

    @Column(name = "trays_per_pack", nullable = false)
    int traysPerPack;

    @Column(name = "stickers_per_pack", nullable = false)
    int stickersPerPack;

    @Column(name = "reference_overhead", nullable = false, precision = 12, scale = 2)
    BigDecimal referenceOverhead;

    @Column(name = "regular_months", nullable = false)
    int regularMonths;

    @Column(name = "recontact_days", nullable = false)
    int recontactDays;

    @Column(name = "price_rise_alert", nullable = false, precision = 5, scale = 4)
    BigDecimal priceRiseAlert;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "sale_reasons", nullable = false)
    List<String> saleReasons;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "expense_types", nullable = false)
    List<String> expenseTypes;
}
