package com.fooda.api.user.infrastructure.out.persistence;

import com.fooda.api.shared.infrastructure.out.persistence.AuditedJpaEntity;
import com.fooda.api.user.domain.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
class UserJpaEntity extends AuditedJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    Long id;

    @Column(name = "email", nullable = false, length = 120)
    String email;

    @Column(name = "full_name", nullable = false, length = 120)
    String fullName;

    @Column(name = "password_hash", nullable = false, length = 100)
    String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    Role role;

    @Column(name = "email_verified", nullable = false)
    boolean emailVerified;

    @Column(name = "active", nullable = false)
    boolean active;
}
