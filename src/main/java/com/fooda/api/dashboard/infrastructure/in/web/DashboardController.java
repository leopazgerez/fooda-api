package com.fooda.api.dashboard.infrastructure.in.web;

import com.fooda.api.dashboard.application.port.in.DashboardUseCase;
import com.fooda.api.dashboard.application.port.in.DashboardView;
import com.fooda.api.finance.application.port.in.MonthlyResponse;
import com.fooda.api.production.application.port.in.ProductionSummary;
import com.fooda.api.stock.application.port.in.StockView;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardUseCase dashboard;

    public DashboardController(DashboardUseCase dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    public DashboardResponse get() {
        DashboardView v = dashboard.get();
        return new DashboardResponse(v.today(), MonthlyResponse.from(v.month()), v.salesPreviousMonthSamePeriod(),
                v.stockByBranch(), v.salesByPack(), v.productions(), v.payback(), v.notices());
    }

    public record DashboardResponse(LocalDate today, MonthlyResponse month, BigDecimal salesPreviousMonthSamePeriod,
                                    List<StockView.BranchTotal> stockByBranch, List<DashboardView.PackSales> salesByPack,
                                    List<ProductionSummary> productions, DashboardView.PaybackView payback,
                                    DashboardView.Notices notices) {
    }
}
