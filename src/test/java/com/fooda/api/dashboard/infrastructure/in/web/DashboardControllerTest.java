package com.fooda.api.dashboard.infrastructure.in.web;

import com.fooda.api.config.SecurityConfig;
import com.fooda.api.config.TimeConfig;
import com.fooda.api.core.domain.finance.AppliedOverhead;
import com.fooda.api.core.domain.finance.MonthlyReport;
import com.fooda.api.dashboard.application.port.in.DashboardUseCase;
import com.fooda.api.dashboard.application.port.in.DashboardView;
import com.fooda.api.finance.application.port.in.MonthlyView;
import com.fooda.api.shared.infrastructure.in.web.GlobalExceptionHandler;
import com.fooda.api.user.application.port.out.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, TimeConfig.class})
class DashboardControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean DashboardUseCase dashboard;
    @MockitoBean UserRepository users;

    private static RequestPostProcessor as(String authority) {
        return jwt().jwt(j -> j.subject("5")).authorities(new SimpleGrantedAuthority(authority));
    }

    @Test
    void dashboardNeedsFinanceRead() throws Exception {
        BigDecimal z = BigDecimal.ZERO;
        var r = new MonthlyReport(YearMonth.of(2026, 11), 0, 0, z, z, z, z, z, new AppliedOverhead(z, false, false),
                z, z, z, List.of());
        given(dashboard.get()).willReturn(new DashboardView(LocalDate.of(2026, 11, 16),
                new MonthlyView(r, null), new BigDecimal("110"), List.of(), List.of(), List.of(),
                new DashboardView.PaybackView("P1", 10, 2),
                new DashboardView.Notices(List.of(), List.of(), List.of(), List.of())));

        mvc.perform(get("/api/dashboard")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/dashboard").with(as("SALES_READ"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard").with(as("FINANCE_READ"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.today").value("2026-11-16"))
                .andExpect(jsonPath("$.month.month").value("2026-11"))
                .andExpect(jsonPath("$.salesPreviousMonthSamePeriod").value(110))
                .andExpect(jsonPath("$.payback.code").value("P1"))
                .andExpect(jsonPath("$.notices.followUps").isArray());
    }
}
