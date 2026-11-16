package com.fooda.api.sales.infrastructure.out.persistence;

import com.fooda.api.shared.infrastructure.out.persistence.AuditedJpaEntity;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "customer")
@SQLRestriction("deleted_at IS NULL")
class CustomerJpaEntity extends AuditedJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "customer_id")
    Long id;

    @Column(name = "first_name", nullable = false, length = 80)
    String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    String lastName;

    @Column(name = "phone", length = 40)
    String phone;

    @Column(name = "address", length = 200)
    String address;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "birthday", length = 5)
    String birthday;

    @Column(name = "notes", length = 500)
    String notes;
}
