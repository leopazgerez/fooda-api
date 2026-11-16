package com.fooda.api.sales.infrastructure.out.persistence;

import com.fooda.api.core.application.port.out.SoldPacks;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Provisional {@link SoldPacks}: there are no sales yet. Block 4 (sales) replaces it with the real adapter. */
@Component
public class NoSales implements SoldPacks {

    @Override
    public Map<Long, Integer> soldByProduction() {
        return Map.of();
    }
}
