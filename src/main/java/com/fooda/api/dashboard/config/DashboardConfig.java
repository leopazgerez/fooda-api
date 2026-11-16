package com.fooda.api.dashboard.config;

import com.fooda.api.core.application.port.out.BranchRepository;
import com.fooda.api.core.application.port.out.PackRepository;
import com.fooda.api.core.application.port.out.SaleRepository;
import com.fooda.api.dashboard.application.port.in.DashboardUseCase;
import com.fooda.api.dashboard.application.service.DashboardService;
import com.fooda.api.finance.application.port.in.FinanceUseCase;
import com.fooda.api.production.application.port.in.ProductionUseCase;
import com.fooda.api.production.application.port.in.PurchaseUseCase;
import com.fooda.api.sales.application.port.in.CustomerUseCase;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.stock.application.port.in.StockUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DashboardConfig {

    @Bean
    DashboardUseCase dashboardUseCase(FinanceUseCase finance, StockUseCase stock, ProductionUseCase productions,
                                      PurchaseUseCase purchases, CustomerUseCase customers, SaleRepository sales,
                                      PackRepository packs, BranchRepository branches, BusinessCalendar calendar) {
        return new DashboardService(finance, stock, productions, purchases, customers, sales, packs, branches,
                calendar);
    }
}
