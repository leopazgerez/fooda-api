package com.fooda.api.finance.infrastructure.in.web;

import com.fooda.api.config.SecurityConfig;
import com.fooda.api.config.TimeConfig;
import com.fooda.api.core.domain.finance.AppliedOverhead;
import com.fooda.api.core.domain.finance.MonthlyReport;
import com.fooda.api.finance.application.port.in.FinanceUseCase;
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

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FinanceController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, TimeConfig.class})
class FinanceControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean FinanceUseCase finance;
    @MockitoBean UserRepository users;

    private static RequestPostProcessor as(String authority) {
        return jwt().jwt(j -> j.subject("5")).authorities(new SimpleGrantedAuthority(authority));
    }

    private static MonthlyView view() {
        var r = new MonthlyReport(YearMonth.of(2026, 11), 50, 10, new BigDecimal("0.2"), new BigDecimal("450000"),
                new BigDecimal("400000"), new BigDecimal("100000"), new BigDecimal("300000"),
                new AppliedOverhead(new BigDecimal("1000"), false, false), new BigDecimal("90000"),
                new BigDecimal("2.0"), new BigDecimal("6.7"),
                List.of(new MonthlyReport.ReserveShare("Ahorro", new BigDecimal("20"), new BigDecimal("18000"))));
        return new MonthlyView(r, LocalDate.of(2026, 11, 15));
    }

    @Test
    void monthlyNeedsFinanceRead() throws Exception {
        given(finance.monthly()).willReturn(List.of(view()));
        mvc.perform(get("/api/finance/monthly").with(as("SALES_READ"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/finance/monthly").with(as("FINANCE_READ"))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].month").value("2026-11"))
                .andExpect(jsonPath("$[0].closed").value(true))
                .andExpect(jsonPath("$[0].overhead.provisional").value(false))
                .andExpect(jsonPath("$[0].reserves[0].name").value("Ahorro"));
    }

    @Test
    void closeReturns201WithLocation() throws Exception {
        given(finance.close("2026-11")).willReturn(view());
        mvc.perform(post("/api/finance/month-closes").with(as("FINANCE_WRITE")).contentType(APPLICATION_JSON)
                        .content("{\"month\":\"2026-11\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/finance/month-closes/2026-11")))
                .andExpect(jsonPath("$.month").value("2026-11"));
    }

    @Test
    void readOnlyUserCannotCloseNorReopen() throws Exception {
        mvc.perform(post("/api/finance/month-closes").with(as("FINANCE_READ")).contentType(APPLICATION_JSON)
                .content("{\"month\":\"2026-11\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/finance/month-closes/2026-11").with(as("FINANCE_READ")))
                .andExpect(status().isForbidden());
    }

    @Test
    void reopenReturns204() throws Exception {
        mvc.perform(delete("/api/finance/month-closes/2026-11").with(as("FINANCE_WRITE")))
                .andExpect(status().isNoContent());
        verify(finance).reopen("2026-11", 5L);
    }
}
