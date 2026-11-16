package com.fooda.api.catalog.application.port.in;

import com.fooda.api.core.domain.catalog.Settings;

public interface SettingsUseCase {
    Settings get();
    /** Keeps the client's version for the optimistic lock. */
    Settings replace(Settings s);
}
