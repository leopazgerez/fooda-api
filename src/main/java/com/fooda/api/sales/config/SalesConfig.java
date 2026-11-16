package com.fooda.api.sales.config;

import com.fooda.api.core.application.port.out.BranchRepository;
import com.fooda.api.core.application.port.out.CustomerRepository;
import com.fooda.api.core.application.port.out.ExpenseRepository;
import com.fooda.api.core.application.port.out.MonthCloseRepository;
import com.fooda.api.core.application.port.out.PackRepository;
import com.fooda.api.core.application.port.out.PaymentRepository;
import com.fooda.api.core.application.port.out.ProductionRepository;
import com.fooda.api.core.application.port.out.SaleRepository;
import com.fooda.api.core.application.port.out.SettingsRepository;
import com.fooda.api.core.application.port.out.StockLock;
import com.fooda.api.core.application.port.out.StockMovementRepository;
import com.fooda.api.core.application.port.out.SupplyRepository;
import com.fooda.api.sales.application.port.in.CustomerUseCase;
import com.fooda.api.sales.application.port.in.ExpenseUseCase;
import com.fooda.api.sales.application.service.CustomerService;
import com.fooda.api.sales.application.port.in.SaleUseCase;
import com.fooda.api.sales.application.service.ExpenseService;
import com.fooda.api.sales.application.service.SaleService;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.shared.application.port.out.Transactions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SalesConfig {

    @Bean
    ExpenseUseCase expenseUseCase(ExpenseRepository expenses, SettingsRepository settings,
                                  MonthCloseRepository monthCloses, StockLock stockLock,
                                  BusinessCalendar calendar, Transactions tx) {
        return new ExpenseService(expenses, settings, monthCloses, stockLock, calendar, tx);
    }

    @Bean
    CustomerUseCase customerUseCase(CustomerRepository customers, SaleRepository sales, PackRepository packs,
                                    SettingsRepository settings, StockLock stockLock, BusinessCalendar calendar,
                                    Transactions tx) {
        return new CustomerService(customers, sales, packs, settings, stockLock, calendar, tx);
    }

    @Bean
    SaleUseCase saleUseCase(SaleRepository sales, PaymentRepository payments, ExpenseRepository expenses,
                            CustomerRepository customers, BranchRepository branches, PackRepository packs,
                            ProductionRepository productions, SupplyRepository supplies,
                            SettingsRepository settings, StockMovementRepository movements,
                            MonthCloseRepository monthCloses, StockLock stockLock, BusinessCalendar calendar,
                            Transactions tx) {
        return new SaleService(sales, payments, expenses, customers, branches, packs, productions, supplies,
                settings, movements, monthCloses, stockLock, calendar, tx);
    }
}
