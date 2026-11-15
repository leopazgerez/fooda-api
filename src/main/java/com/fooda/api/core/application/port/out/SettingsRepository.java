package com.fooda.api.core.application.port.out;

import com.fooda.api.core.domain.catalog.Settings;

import java.util.Optional;

public interface SettingsRepository {

    Optional<Settings> find();

    Settings save(Settings settings);
}
