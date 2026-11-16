package com.fooda.api.production.config;

import com.fooda.api.core.application.port.out.BranchRepository;
import com.fooda.api.core.application.port.out.DishRepository;
import com.fooda.api.core.application.port.out.ExpenseRepository;
import com.fooda.api.core.application.port.out.MonthCloseRepository;
import com.fooda.api.core.application.port.out.PackRepository;
import com.fooda.api.core.application.port.out.ProductionRepository;
import com.fooda.api.core.application.port.out.PurchaseRepository;
import com.fooda.api.core.application.port.out.SettingsRepository;
import com.fooda.api.core.application.port.out.SoldPacks;
import com.fooda.api.core.application.port.out.StockLock;
import com.fooda.api.core.application.port.out.StockMovementRepository;
import com.fooda.api.core.application.port.out.SupplierRepository;
import com.fooda.api.core.application.port.out.SupplyRepository;
import com.fooda.api.core.application.port.out.TransferRepository;
import com.fooda.api.production.application.service.ProductionService;
import com.fooda.api.production.application.service.PurchasePlanner;
import com.fooda.api.production.application.service.PurchaseService;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.shared.application.port.out.Transactions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Factory for the production module: each service is the single bean behind its use case. */
@Configuration(proxyBeanMethods = false)
public class ProductionConfig {

    @Bean
    PurchasePlanner purchasePlanner(PurchaseRepository purchases, SupplyRepository supplies,
                                    SupplierRepository suppliers) {
        return new PurchasePlanner(purchases, supplies, suppliers);
    }

    @Bean
    ProductionService productionService(ProductionRepository productions, PurchaseRepository purchases,
                                        PackRepository packs, DishRepository dishes, SupplyRepository supplies,
                                        SupplierRepository suppliers, SettingsRepository settings,
                                        ExpenseRepository expenses, MonthCloseRepository monthCloses,
                                        SoldPacks soldPacks, TransferRepository transfers,
                                        StockMovementRepository movements, BranchRepository branches,
                                        StockLock stockLock, PurchasePlanner planner, BusinessCalendar calendar,
                                        Transactions tx) {
        return new ProductionService(productions, purchases, packs, dishes, supplies, suppliers, settings, expenses,
                monthCloses, soldPacks, transfers, movements, branches, stockLock, planner, calendar, tx);
    }

    @Bean
    PurchaseService purchaseService(SupplyRepository supplies, SupplierRepository suppliers,
                                    SettingsRepository settings, ProductionRepository productions,
                                    PurchaseRepository purchases, ExpenseRepository expenses,
                                    MonthCloseRepository monthCloses, StockLock stockLock, PurchasePlanner planner,
                                    BusinessCalendar calendar, Transactions tx) {
        return new PurchaseService(supplies, suppliers, settings, productions, purchases, expenses, monthCloses,
                stockLock, planner, calendar, tx);
    }
}
