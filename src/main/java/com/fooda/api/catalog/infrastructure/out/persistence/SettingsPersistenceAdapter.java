package com.fooda.api.catalog.infrastructure.out.persistence;

import com.fooda.api.core.application.port.out.SettingsRepository;
import com.fooda.api.core.domain.catalog.Settings;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class SettingsPersistenceAdapter implements SettingsRepository {

    private final SettingsJpaRepository repository;

    public SettingsPersistenceAdapter(SettingsJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Settings> find() {
        return repository.findById(Boolean.TRUE).map(SettingsPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public Settings save(Settings s) {
        return toDomain(repository.saveAndFlush(toEntity(s)));
    }

    private static Settings toDomain(SettingsJpaEntity e) {
        return new Settings(e.packPrice, e.oilMlPerPack, e.oilSupplyId, e.traysPerPack, e.stickersPerPack,
                e.referenceOverhead, e.regularMonths, e.recontactDays, e.priceRiseAlert,
                e.saleReasons, e.expenseTypes, e.getVersion());
    }

    private static SettingsJpaEntity toEntity(Settings s) {
        SettingsJpaEntity e = new SettingsJpaEntity();
        e.packPrice = s.packPrice();
        e.oilMlPerPack = s.oilMlPerPack();
        e.oilSupplyId = s.oilSupplyId();
        e.traysPerPack = s.traysPerPack();
        e.stickersPerPack = s.stickersPerPack();
        e.referenceOverhead = s.referenceOverhead();
        e.regularMonths = s.regularMonths();
        e.recontactDays = s.recontactDays();
        e.priceRiseAlert = s.priceRiseAlert();
        e.saleReasons = s.saleReasons();
        e.expenseTypes = s.expenseTypes();
        e.setVersion(s.version());
        return e;
    }
}
