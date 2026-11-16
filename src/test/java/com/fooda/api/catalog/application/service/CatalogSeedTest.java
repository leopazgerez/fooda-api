package com.fooda.api.catalog.application.service;

import com.fooda.api.catalog.fakes.InMemoryBranchRepository;
import com.fooda.api.catalog.fakes.InMemorySettingsRepository;
import com.fooda.api.shared.DirectTransactions;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogSeedTest {

    private final InMemorySettingsRepository settings = new InMemorySettingsRepository();
    private final InMemoryBranchRepository branches = new InMemoryBranchRepository();
    private final CatalogSeed seed = new CatalogSeed(settings, branches, new DirectTransactions());

    @Test
    void seedsOnceWithFrontDefaults() {
        assertThat(seed.seedIfEmpty()).isTrue();
        assertThat(settings.find().orElseThrow().packPrice()).isEqualByComparingTo("45000");
        assertThat(branches.findKitchen()).isPresent();
        assertThat(seed.seedIfEmpty()).isFalse();
    }
}
