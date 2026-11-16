package com.fooda.api.production.infrastructure.in.web;

import com.fooda.api.config.SecurityConfig;
import com.fooda.api.config.TimeConfig;
import com.fooda.api.shared.infrastructure.in.web.GlobalExceptionHandler;
import com.fooda.api.user.application.port.out.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fooda.api.core.domain.production.ProductionStatus;
import com.fooda.api.core.domain.finance.AppliedOverhead;
import com.fooda.api.core.domain.finance.Payback;
import com.fooda.api.production.application.port.in.ProductionSummary;
import com.fooda.api.production.application.port.in.ProductionUseCase;
import com.fooda.api.shared.domain.ConflictException;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductionsController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, TimeConfig.class})
class ProductionsControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ProductionUseCase productions;
    @MockitoBean UserRepository users;

    private static ProductionSummary summary() {
        return new ProductionSummary(7, "P-1", 1, ProductionStatus.PLANNED, LocalDate.of(2026, 11, 2), null, 10,
                new BigDecimal("100"), null, 0, BigDecimal.ZERO, new AppliedOverhead(BigDecimal.TEN, true, false),
                List.of(new ProductionSummary.PackLine(1, "Pack", 10, null, 4L, BigDecimal.ONE, null,
                        BigDecimal.ONE)), new Payback(3, 1), 4L);
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor write() {
        return jwt().jwt(j -> j.subject("5")).authorities(new SimpleGrantedAuthority("PRODUCTION_WRITE"));
    }

    @Test
    void readOnlyCannotPlan() throws Exception {
        mvc.perform(post("/api/productions").with(jwt().authorities(new SimpleGrantedAuthority("PRODUCTION_READ")))
                        .contentType(APPLICATION_JSON).content("{\"lines\":[]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void planReturns201WithLocation() throws Exception {
        given(productions.plan(any(), any(), eq(5L))).willReturn(summary());
        mvc.perform(post("/api/productions").with(write()).contentType(APPLICATION_JSON)
                        .content("{\"lines\":[{\"packId\":1,\"planned\":10}]}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/productions/7")))
                .andExpect(jsonPath("$.status").value("planned"))
                .andExpect(jsonPath("$.overhead.provisional").value(true))
                .andExpect(jsonPath("$.packLines[0].packId").value(1))
                .andExpect(jsonPath("$.payback.missing").value(1));
        verify(productions).plan(argThat(l -> l.get(0).packId() == 1 && l.get(0).planned() == 10), eq(null), eq(5L));
    }

    @Test
    void missingLinesIs400WithField() throws Exception {
        mvc.perform(post("/api/productions").with(write()).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("lines"));
    }

    @Test
    void closeMapsBodyToCloseRequest() throws Exception {
        given(productions.close(eq(7L), any())).willReturn(summary());
        mvc.perform(post("/api/productions/7/close").with(write()).contentType(APPLICATION_JSON)
                        .content("{\"date\":\"2026-11-10\",\"lines\":[{\"packId\":1,\"actual\":9}],"
                                + "\"distribution\":[{\"branchId\":3,\"packId\":1,\"quantity\":4}]}"))
                .andExpect(status().isOk());
        verify(productions).close(eq(7L), argThat(r -> r.distribution().get(0).branchId() == 3
                && r.lines().get(0).actual() == 9 && r.date().equals(LocalDate.of(2026, 11, 10))));
    }

    @Test
    void leftoversKeysAreSupplyIds() throws Exception {
        given(productions.setLeftovers(eq(7L), any(), any(), eq(5L))).willReturn(summary());
        mvc.perform(patch("/api/productions/7/leftovers").with(write()).contentType(APPLICATION_JSON)
                        .content("{\"leftovers\":{\"12\":2.5}}"))
                .andExpect(status().isOk());
        verify(productions).setLeftovers(eq(7L), argThat(m -> m.get(12L).compareTo(new BigDecimal("2.5")) == 0),
                eq(null), eq(5L));
    }

    @Test
    void conflictMapsTo409() throws Exception {
        given(productions.reopen(7L, 5L)).willThrow(new ConflictException("status", "Ya está cerrada"));
        mvc.perform(post("/api/productions/7/reopen").with(write()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.field").value("status"));
    }

    @Test
    void deleteIs204AndPassesActor() throws Exception {
        mvc.perform(delete("/api/productions/7").with(write())).andExpect(status().isNoContent());
        verify(productions).annul(7L, 5L);
    }

    @Test
    void closeWithoutLinesUsesPlanned() throws Exception {
        given(productions.close(eq(7L), any())).willReturn(summary());
        mvc.perform(post("/api/productions/7/close").with(write()).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        verify(productions).close(eq(7L), argThat(r -> r.lines().isEmpty() && r.distribution().isEmpty()));
    }

    @Test
    void getKeepsFlatShape() throws Exception {
        given(productions.get(7L)).willReturn(new com.fooda.api.production.application.port.in.ProductionDetail(
                summary(), java.util.Map.of(12L, BigDecimal.ONE), List.of()));
        mvc.perform(get("/api/productions/7").with(jwt().authorities(new SimpleGrantedAuthority("PRODUCTION_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("P-1"))
                .andExpect(jsonPath("$.leftovers.12").value(1))
                .andExpect(jsonPath("$.transfers").isArray());
    }

    @Test
    void leftoversPassesVersionAndRejectsExplicitNull() throws Exception {
        given(productions.setLeftovers(eq(7L), any(), eq(3L), eq(5L))).willReturn(summary());
        mvc.perform(patch("/api/productions/7/leftovers").with(write()).contentType(APPLICATION_JSON)
                        .content("{\"leftovers\":{},\"version\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(4));
        mvc.perform(patch("/api/productions/7/leftovers").with(write()).contentType(APPLICATION_JSON)
                        .content("{\"leftovers\":{},\"version\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("version"));
        given(productions.setLeftovers(eq(7L), any(), eq(2L), eq(5L)))
                .willThrow(new OptimisticLockingFailureException("stale"));
        mvc.perform(patch("/api/productions/7/leftovers").with(write()).contentType(APPLICATION_JSON)
                        .content("{\"leftovers\":{},\"version\":2}"))
                .andExpect(status().isConflict());
    }

    @Test
    void closeWithoutBodyUsesEmptyRequest() throws Exception {
        given(productions.close(eq(7L), any())).willReturn(summary());
        mvc.perform(post("/api/productions/7/close").with(write())).andExpect(status().isOk());
        verify(productions).close(eq(7L), argThat(r -> r.date() == null && r.lines().isEmpty()
                && r.distribution().isEmpty()));
    }
}
