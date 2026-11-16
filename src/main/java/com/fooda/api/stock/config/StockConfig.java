package com.fooda.api.stock.config;

import com.fooda.api.core.application.port.out.BranchRepository;
import com.fooda.api.core.application.port.out.MonthCloseRepository;
import com.fooda.api.core.application.port.out.StockLock;
import com.fooda.api.core.application.port.out.TransferRepository;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.shared.application.port.out.Transactions;
import com.fooda.api.stock.application.port.in.TransferUseCase;
import com.fooda.api.stock.application.service.TransferService;
import com.fooda.api.core.application.port.out.PackRepository;
import com.fooda.api.core.application.port.out.ProductionRepository;
import com.fooda.api.core.application.port.out.SettingsRepository;
import com.fooda.api.core.application.port.out.StockMovementRepository;
import com.fooda.api.stock.application.port.in.AdjustmentUseCase;
import com.fooda.api.stock.application.port.in.StockUseCase;
import com.fooda.api.stock.application.service.AdjustmentService;
import com.fooda.api.stock.application.service.StockService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class StockConfig {

    @Bean
    StockUseCase stockUseCase(StockMovementRepository movements, ProductionRepository productions,
                              PackRepository packs, BranchRepository branches, SettingsRepository settings) {
        return new StockService(movements, productions, packs, branches, settings);
    }

    @Bean
    TransferUseCase transferUseCase(TransferRepository transfers, StockMovementRepository movements,
                                    BranchRepository branches, PackRepository packs, ProductionRepository productions,
                                    MonthCloseRepository monthCloses, StockLock stockLock, BusinessCalendar calendar,
                                    Transactions tx) {
        return new TransferService(transfers, movements, branches, packs, productions, monthCloses, stockLock,
                calendar, tx);
    }

    @Bean
    AdjustmentUseCase adjustmentUseCase(StockMovementRepository movements, BranchRepository branches,
                                        PackRepository packs, ProductionRepository productions,
                                        SettingsRepository settings, MonthCloseRepository monthCloses,
                                        StockLock stockLock, BusinessCalendar calendar, Transactions tx) {
        return new AdjustmentService(movements, branches, packs, productions, settings, monthCloses, stockLock,
                calendar, tx);
    }
}
