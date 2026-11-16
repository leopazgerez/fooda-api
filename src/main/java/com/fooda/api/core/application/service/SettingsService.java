package com.fooda.api.core.application.service;

import com.fooda.api.core.application.port.in.SettingsUseCase;
import com.fooda.api.core.application.port.out.SettingsRepository;
import com.fooda.api.core.application.port.out.SupplyRepository;
import com.fooda.api.core.domain.catalog.Settings;
import com.fooda.api.shared.application.exception.ResourceNotFoundException;
import com.fooda.api.shared.application.port.out.Transactions;
import com.fooda.api.shared.domain.ValidationException;

public class SettingsService implements SettingsUseCase {

    private final SettingsRepository settings;
    private final SupplyRepository supplies;
    private final Transactions tx;

    public SettingsService(SettingsRepository settings, SupplyRepository supplies, Transactions tx) {
        this.settings = settings;
        this.supplies = supplies;
        this.tx = tx;
    }

    @Override
    public Settings get() {
        return settings.find().orElseThrow(() -> new ResourceNotFoundException("Todavía no se cargaron los parámetros"));
    }

    @Override
    public Settings replace(Settings s) {
        return tx.get(() -> {
            if (s.oilSupplyId() != null && supplies.findById(s.oilSupplyId()).isEmpty()) {
                throw new ValidationException("oilSupplyId", "Insumo no encontrado");
            }
            return settings.save(s);
        });
    }
}
