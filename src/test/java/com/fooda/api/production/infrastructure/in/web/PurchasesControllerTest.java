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

import com.fooda.api.core.domain.catalog.Unit;
import com.fooda.api.core.domain.production.Purchase;
import com.fooda.api.core.domain.production.PurchaseStatus;
import com.fooda.api.production.application.port.in.BoughtPurchase;
import com.fooda.api.production.application.port.in.PackagingStatus;
import com.fooda.api.production.application.port.in.PriceAlert;
import com.fooda.api.production.application.port.in.PurchaseUseCase;
import com.fooda.api.production.application.port.in.PurchaseView;
import com.fooda.api.shared.application.exception.ResourceNotFoundException;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PurchasesController.class, PackagingController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, TimeConfig.class})
class PurchasesControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean PurchaseUseCase purchases;
    @MockitoBean UserRepository users;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor write() {
        return jwt().jwt(j -> j.subject("5")).authorities(new SimpleGrantedAuthority("PRODUCTION_WRITE"));
    }

    private static BoughtPurchase bought() {
        var p = new Purchase(9L, null, 2, PurchaseStatus.BOUGHT, LocalDate.of(2026, 11, 3), List.of(),
                new BigDecimal("500"), 1L);
        var v = new PurchaseView(p, "Granja", null, new BigDecimal("500"), List.of(
                new PurchaseView.LineView(4, BigDecimal.ONE, BigDecimal.TEN, "Harina", Unit.G, BigDecimal.TEN)));
        return new BoughtPurchase(v, List.of(new PriceAlert(4, "Harina", BigDecimal.ONE, BigDecimal.TEN,
                BigDecimal.TEN)));
    }

    @Test
    void buyReturnsAlerts() throws Exception {
        given(purchases.buy(eq(9L), any(), any())).willReturn(bought());
        mvc.perform(post("/api/purchases/9/buy").with(write()).contentType(APPLICATION_JSON)
                        .content("{\"lines\":[{\"supplyId\":4,\"quantity\":1,\"packagePrice\":10}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alerts[0].supplyId").value(4))
                .andExpect(jsonPath("$.purchase.status").value("bought"))
                .andExpect(jsonPath("$.purchase.lines[0].unit").value("g"));
    }

    @Test
    void buyLooseReturns201() throws Exception {
        given(purchases.buyLoose(eq(2L), any(), any())).willReturn(bought());
        mvc.perform(post("/api/purchases").with(write()).contentType(APPLICATION_JSON)
                        .content("{\"supplierId\":2,\"lines\":[{\"supplyId\":4,\"quantity\":1,\"packagePrice\":10}]}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/purchases/9")));
        verify(purchases).buyLoose(eq(2L), eq(null), argThat(l -> l.get(0).supplyId() == 4));
    }

    @Test
    void missingLinesIs400WithField() throws Exception {
        mvc.perform(post("/api/purchases/9/buy").with(write()).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.field").value("lines"));
    }

    @Test
    void readOnlyCannotDelete() throws Exception {
        mvc.perform(delete("/api/purchases/9").with(jwt().authorities(new SimpleGrantedAuthority("PRODUCTION_READ"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteIs204AndPassesActor() throws Exception {
        mvc.perform(delete("/api/purchases/9").with(write())).andExpect(status().isNoContent());
        verify(purchases).annul(9L, 5L);
    }

    @Test
    void packagingReadable() throws Exception {
        var item = new PackagingStatus.Item(1L, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, true);
        given(purchases.packaging()).willReturn(new PackagingStatus(item, item));
        mvc.perform(get("/api/packaging").with(jwt().authorities(new SimpleGrantedAuthority("PRODUCTION_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tray.enough").value(true));
    }

    @Test
    void getByIdIsReadableAndMissingSupplyHasNullUnit() throws Exception {
        var p = new Purchase(9L, 3L, 2, PurchaseStatus.PENDING, null, List.of(), null, 1L);
        given(purchases.get(9L)).willReturn(new PurchaseView(p, "Granja", "P-1", new BigDecimal("500"), List.of(
                new PurchaseView.LineView(4, BigDecimal.ONE, BigDecimal.TEN, "—", null, null))));
        mvc.perform(get("/api/purchases/9").with(jwt().authorities(new SimpleGrantedAuthority("PRODUCTION_READ"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9))
                .andExpect(jsonPath("$.production").value("P-1"))
                .andExpect(jsonPath("$.lines[0].supply").value("—"))
                .andExpect(jsonPath("$.lines[0].unit").doesNotExist());
    }

    @Test
    void missingPurchaseIs404() throws Exception {
        given(purchases.get(9L)).willThrow(new ResourceNotFoundException("Compra no encontrada"));
        mvc.perform(get("/api/purchases/9").with(jwt().authorities(new SimpleGrantedAuthority("PRODUCTION_READ"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Compra no encontrada"));
    }

    @Test
    void lineInputsAreRoundedToColumnScales() throws Exception {
        given(purchases.buy(eq(9L), any(), any())).willReturn(bought());
        mvc.perform(post("/api/purchases/9/buy").with(write()).contentType(APPLICATION_JSON)
                        .content("{\"lines\":[{\"supplyId\":4,\"quantity\":1.23456,\"packagePrice\":10.005}]}"))
                .andExpect(status().isOk());
        verify(purchases).buy(eq(9L), eq(null), argThat(l -> l.get(0).quantity().equals(new BigDecimal("1.235"))
                && l.get(0).packagePrice().equals(new BigDecimal("10.01"))));
    }
}
