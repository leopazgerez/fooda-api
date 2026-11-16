package com.fooda.api.catalog.config;

import com.fooda.api.catalog.application.service.BranchService;
import com.fooda.api.catalog.application.service.DishService;
import com.fooda.api.catalog.application.service.PackService;
import com.fooda.api.catalog.application.service.ReserveFundService;
import com.fooda.api.catalog.application.service.SettingsService;
import com.fooda.api.catalog.application.service.SupplierService;
import com.fooda.api.catalog.application.service.SupplyService;
import com.fooda.api.core.application.port.out.BranchRepository;
import com.fooda.api.core.application.port.out.DishRepository;
import com.fooda.api.core.application.port.out.PackRepository;
import com.fooda.api.core.application.port.out.ReserveFundRepository;
import com.fooda.api.core.application.port.out.SettingsRepository;
import com.fooda.api.core.application.port.out.SupplierRepository;
import com.fooda.api.core.application.port.out.SupplyRepository;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.shared.application.port.out.Transactions;
import com.fooda.api.catalog.application.service.CatalogSeed;
import com.fooda.api.shared.infrastructure.RunAs;
import com.fooda.api.user.application.port.out.UserRepository;
import com.fooda.api.user.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/** Factory for the catalog module: each service is the single bean behind its use case. */
@Configuration(proxyBeanMethods = false)
public class CatalogConfig {

    private static final Logger LOG = LoggerFactory.getLogger(CatalogConfig.class);

    @Bean
    CatalogSeed catalogSeedService(SettingsRepository settings, BranchRepository branches, Transactions tx) {
        return new CatalogSeed(settings, branches, tx);
    }

    /** Runs after the admin bootstrap (Order 0) so created_by points at the admin. */
    @Bean
    @Order(1)
    ApplicationRunner catalogSeed(CatalogSeed seed, UserRepository users, @Value("${app.admin.email:}") String email) {
        return args -> {
            if (email.isBlank()) {
                LOG.info("APP_ADMIN_EMAIL not set: skipping catalog seed");
                return;
            }
            users.findByEmail(User.normalizeEmail(email)).ifPresent(admin ->
                    RunAs.user(admin.id().value(), () -> {
                        if (seed.seedIfEmpty()) {
                            LOG.info("Seeded initial settings and kitchen branch");
                        }
                    }));
        };
    }

    @Bean
    SupplierService supplierService(SupplierRepository suppliers, SupplyRepository supplies, Transactions tx) {
        return new SupplierService(suppliers, supplies, tx);
    }

    @Bean
    BranchService branchService(BranchRepository branches, Transactions tx) {
        return new BranchService(branches, tx);
    }

    @Bean
    ReserveFundService reserveFundService(ReserveFundRepository funds, Transactions tx) {
        return new ReserveFundService(funds, tx);
    }

    @Bean
    SettingsService settingsService(SettingsRepository settings, SupplyRepository supplies, Transactions tx) {
        return new SettingsService(settings, supplies, tx);
    }

    @Bean
    SupplyService supplyService(SupplyRepository supplies, SupplierRepository suppliers, DishRepository dishes,
                                SettingsRepository settings, BusinessCalendar calendar, Transactions tx) {
        return new SupplyService(supplies, suppliers, dishes, settings, calendar, tx);
    }

    @Bean
    DishService dishService(DishRepository dishes, PackRepository packs, SupplyRepository supplies,
                            BusinessCalendar calendar, Transactions tx) {
        return new DishService(dishes, packs, supplies, calendar, tx);
    }

    @Bean
    PackService packService(PackRepository packs, DishRepository dishes, SupplyRepository supplies,
                            SettingsRepository settings, BusinessCalendar calendar, Transactions tx) {
        return new PackService(packs, dishes, supplies, settings, calendar, tx);
    }
}
