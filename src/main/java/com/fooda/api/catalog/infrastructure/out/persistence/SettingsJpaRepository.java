package com.fooda.api.catalog.infrastructure.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SettingsJpaRepository extends JpaRepository<SettingsJpaEntity, Boolean> {
}
