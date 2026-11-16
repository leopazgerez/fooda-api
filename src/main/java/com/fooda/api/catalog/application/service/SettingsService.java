package com.fooda.api.catalog.application.service;

import com.fooda.api.catalog.application.port.in.SettingsUseCase;
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
            if (s.version() != null) {
                return settings.save(s);
            }
            // no version sent: last write wins over whatever is stored (or the first insert)
            Long current = settings.find().map(Settings::version).orElse(null);
            return settings.save(new Settings(s.packPrice(), s.oilMlPerPack(), s.oilSupplyId(), s.traysPerPack(),
                    s.stickersPerPack(), s.referenceOverhead(), s.regularMonths(), s.recontactDays(),
                    s.priceRiseAlert(), s.saleReasons(), s.expenseTypes(), current));
        });
    }
}
