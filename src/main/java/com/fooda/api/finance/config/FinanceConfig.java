package com.fooda.api.finance.config;

import com.fooda.api.core.application.port.out.BranchRepository;
import com.fooda.api.core.application.port.out.DishRepository;
import com.fooda.api.core.application.port.out.ExpenseRepository;
import com.fooda.api.core.application.port.out.MonthCloseRepository;
import com.fooda.api.core.application.port.out.PackRepository;
import com.fooda.api.core.application.port.out.PaymentRepository;
import com.fooda.api.core.application.port.out.ProductionRepository;
import com.fooda.api.core.application.port.out.ReserveFundRepository;
import com.fooda.api.core.application.port.out.SaleRepository;
import com.fooda.api.core.application.port.out.SettingsRepository;
import com.fooda.api.core.application.port.out.StockLock;
import com.fooda.api.core.application.port.out.SupplyRepository;
import com.fooda.api.finance.application.port.in.AnalysisUseCase;
import com.fooda.api.finance.application.port.in.FinanceUseCase;
import com.fooda.api.finance.application.service.AnalysisService;
import com.fooda.api.finance.application.service.FinanceService;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.shared.application.port.out.Transactions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class FinanceConfig {

    @Bean
    FinanceUseCase financeUseCase(ProductionRepository productions, SaleRepository sales, PaymentRepository payments,
                                  ExpenseRepository expenses, SettingsRepository settings,
                                  ReserveFundRepository funds, SupplyRepository supplies, BranchRepository branches,
                                  MonthCloseRepository monthCloses, StockLock stockLock, BusinessCalendar calendar,
                                  Transactions tx) {
        return new FinanceService(productions, sales, payments, expenses, settings, funds, supplies, branches,
                monthCloses, stockLock, calendar, tx);
    }

    @Bean
    AnalysisUseCase analysisUseCase(SaleRepository sales, PackRepository packs, DishRepository dishes,
                                    BranchRepository branches, BusinessCalendar calendar) {
        return new AnalysisService(sales, packs, dishes, branches, calendar);
    }
}
