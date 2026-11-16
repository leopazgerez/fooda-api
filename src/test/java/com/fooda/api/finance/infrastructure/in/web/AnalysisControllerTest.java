package com.fooda.api.finance.infrastructure.in.web;

import com.fooda.api.config.SecurityConfig;
import com.fooda.api.config.TimeConfig;
import com.fooda.api.core.domain.catalog.MenuSlot;
import com.fooda.api.core.domain.catalog.PackVersion;
import com.fooda.api.finance.application.port.in.AnalysisUseCase;
import com.fooda.api.finance.application.port.in.PackAnalysis;
import com.fooda.api.finance.application.port.in.VersionAt;
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
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalysisController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, TimeConfig.class})
class AnalysisControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean AnalysisUseCase analysis;
    @MockitoBean UserRepository users;

    private static RequestPostProcessor as(String authority) {
        return jwt().jwt(j -> j.subject("5")).authorities(new SimpleGrantedAuthority(authority));
    }

    @Test
    void analysisNeedsFinanceRead() throws Exception {
        var from = LocalDate.of(2026, 9, 1);
        var to = LocalDate.of(2026, 11, 16);
        given(analysis.packs(from, to, "week")).willReturn(new PackAnalysis(from, to, "week",
                List.of(new PackAnalysis.Row("2026-11-09", 5, "Clásico", 2, "JVG", 3, new BigDecimal("270"))),
                List.of(new PackAnalysis.Change(5, "Clásico", LocalDate.of(2026, 10, 5), 9))));
        var v = new PackVersion(9L, 5, LocalDate.of(2026, 10, 5), List.of(new MenuSlot(1, 1), new MenuSlot(2, 2),
                new MenuSlot(3, 3), new MenuSlot(4, 4), new MenuSlot(5, 5)));
        given(analysis.versionAt(5, LocalDate.of(2026, 10, 6))).willReturn(new VersionAt(v, null,
                List.of(new VersionAt.Menu(1, "Tarta")), null, List.of()));

        mvc.perform(get("/api/analysis/packs").param("from", "2026-09-01").param("to", "2026-11-16")
                .param("groupBy", "week").with(as("SALES_READ"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/analysis/packs").param("from", "2026-09-01").param("to", "2026-11-16")
                        .param("groupBy", "week").with(as("FINANCE_READ"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].branch").value("JVG"))
                .andExpect(jsonPath("$.changes[0].versionId").value(9));
        mvc.perform(get("/api/analysis/packs/5/version-at").param("date", "2026-10-06").with(as("FINANCE_READ")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version.id").value(9))
                .andExpect(jsonPath("$.version.menus[0].dishId").value(1))
                .andExpect(jsonPath("$.menus[0].dish").value("Tarta"))
                .andExpect(jsonPath("$.previous").doesNotExist());
    }
}
