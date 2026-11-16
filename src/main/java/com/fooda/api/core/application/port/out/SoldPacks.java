package com.fooda.api.core.application.port.out;

import java.util.Map;

public interface SoldPacks {

    /** Packs sold as income, by production id. */
    Map<Long, Integer> soldByProduction();
}
