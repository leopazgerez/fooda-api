package com.fooda.api.finance.infrastructure.out.persistence;

import com.fooda.api.shared.infrastructure.out.persistence.AuditedJpaEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "month_close")
@SQLRestriction("deleted_at IS NULL")
class MonthCloseJpaEntity extends AuditedJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "month_close_id")
    Long id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "month", nullable = false, length = 7)
    String month;

    @Column(name = "overhead_per_pack", precision = 14, scale = 4)
    BigDecimal overheadPerPack;

    @Column(name = "closed_on", nullable = false)
    LocalDate closedOn;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "report", nullable = false)
    String report;
}
