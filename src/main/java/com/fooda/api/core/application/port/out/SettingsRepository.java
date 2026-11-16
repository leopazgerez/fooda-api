package com.fooda.api.core.application.port.out;

import com.fooda.api.core.domain.catalog.Settings;

import com.fooda.api.shared.application.exception.ResourceNotFoundException;

import java.util.Optional;

public interface SettingsRepository {

    Optional<Settings> find();

    Settings save(Settings settings);

    default Settings require() {
        return find().orElseThrow(() -> new ResourceNotFoundException("Todavía no se cargaron los parámetros"));
    }
}
