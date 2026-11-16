package com.fooda.api.catalog.application.service;

import com.fooda.api.core.application.port.out.BranchRepository;
import com.fooda.api.core.application.port.out.SettingsRepository;
import com.fooda.api.core.domain.catalog.Branch;
import com.fooda.api.core.domain.catalog.Settings;
import com.fooda.api.core.domain.catalog.Shipping;
import com.fooda.api.core.domain.catalog.ShippingType;
import com.fooda.api.shared.application.port.out.Transactions;

import java.math.BigDecimal;
import java.util.List;

/** Initial data the front end assumes: default settings and the central kitchen branch. */
public class CatalogSeed {

    private final SettingsRepository settings;
    private final BranchRepository branches;
    private final Transactions tx;

    public CatalogSeed(SettingsRepository settings, BranchRepository branches, Transactions tx) {
        this.settings = settings;
        this.branches = branches;
        this.tx = tx;
    }

    /** Returns whether it seeded (no settings yet). */
    public boolean seedIfEmpty() {
        return tx.get(() -> {
            if (settings.find().isPresent()) {
                return false;
            }
            settings.save(new Settings(BigDecimal.valueOf(45000), BigDecimal.valueOf(20), null, 5, 1,
                    BigDecimal.valueOf(1000), 2, 30, new BigDecimal("0.15"),
                    List.of("recontacto", "nuevo", "repatriado", "fiel"),
                    List.of("fijo", "mercadería", "inversión", "personal"), null));
            if (branches.findKitchen().isEmpty()) {
                branches.save(new Branch(null, "Salta", "Cocina central, Salta Capital", 300, 20, 999,
                        new Shipping(ShippingType.AMOUNT, BigDecimal.ZERO), false, true, null));
            }
            return true;
        });
    }
}
