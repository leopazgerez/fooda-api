package com.fooda.api.stock.infrastructure.in.web;

import com.fooda.api.config.SecurityConfig;
import com.fooda.api.config.TimeConfig;
import com.fooda.api.core.domain.stock.BranchAlert;
import com.fooda.api.core.domain.stock.MovementType;
import com.fooda.api.core.domain.stock.StockMovement;
import com.fooda.api.core.domain.stock.Transfer;
import com.fooda.api.shared.infrastructure.in.web.GlobalExceptionHandler;
import com.fooda.api.stock.application.port.in.AdjustmentUseCase;
import com.fooda.api.stock.application.port.in.StockUseCase;
import com.fooda.api.stock.application.port.in.StockView;
import com.fooda.api.stock.application.port.in.TransferUseCase;
import com.fooda.api.stock.application.port.in.TransferView;
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

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({StockController.class, TransfersController.class, StockAdjustmentsController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, TimeConfig.class})
class StockWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean StockUseCase stock;
    @MockitoBean TransferUseCase transfers;
    @MockitoBean AdjustmentUseCase adjustments;
    @MockitoBean UserRepository users;

    private static RequestPostProcessor as(String authority) {
        return jwt().jwt(j -> j.subject("5")).authorities(new SimpleGrantedAuthority(authority));
    }

    private static TransferView transferView() {
        var t = new Transfer(7L, LocalDate.of(2026, 11, 3), 1, 2, "n", null, List.of());
        return new TransferView(t, "A", "B", 3, List.of(new TransferView.LineView(4, "L1", 5, "Caja", 3)));
    }

    @Test
    void stockRequiresStockRead() throws Exception {
        given(stock.view(any(), any(), any())).willReturn(new StockView(List.of(), List.of(
                new StockView.BranchTotal(1, "A", 2, 10, 8, List.of(BranchAlert.LOW_STOCK)))));
        mvc.perform(get("/api/stock").with(as("STOCK_WRITE"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/stock?branchId=1").with(as("STOCK_READ"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.byBranch[0].alerts[0]").value("low_stock"));
        verify(stock).view(1L, null, null);
    }

    @Test
    void createTransferParsesTextIdsAndAnswers201WithLocation() throws Exception {
        given(transfers.create(any())).willReturn(transferView());
        mvc.perform(post("/api/transfers").with(as("STOCK_WRITE")).contentType(APPLICATION_JSON).content(
                        "{\"originBranchId\":\"1\",\"destinationBranchId\":\"2\",\"lines\":"
                                + "[{\"productionId\":\"4\",\"packId\":\"5\",\"quantity\":3}]}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/transfers/7")))
                .andExpect(jsonPath("$.origin").value("A"))
                .andExpect(jsonPath("$.lines[0].lot").value("L1"));
        verify(transfers).create(argThat(r -> r.originBranchId() == 1L && r.lines().get(0).packId() == 5L
                && r.lines().get(0).quantity() == 3));
    }

    @Test
    void transferLineWithDecimalQuantityIs400() throws Exception {
        mvc.perform(post("/api/transfers").with(as("STOCK_WRITE")).contentType(APPLICATION_JSON).content(
                        "{\"originBranchId\":1,\"destinationBranchId\":2,\"lines\":"
                                + "[{\"productionId\":4,\"packId\":5,\"quantity\":1.5}]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/transfers").with(as("STOCK_WRITE")).contentType(APPLICATION_JSON).content(
                        "{\"originBranchId\":1,\"destinationBranchId\":2,\"lines\":[null]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(transfers);
    }

    @Test
    void deleteTransferIs204() throws Exception {
        mvc.perform(delete("/api/transfers/7").with(as("STOCK_WRITE"))).andExpect(status().isNoContent());
        verify(transfers).annul(7L, 5L);
    }

    @Test
    void createAdjustmentAnswers201() throws Exception {
        given(adjustments.create(any())).willReturn(new StockMovement(9L, LocalDate.of(2026, 11, 3), 1, 4, 5, -2,
                MovementType.ADJUSTMENT, null, "rotura"));
        mvc.perform(post("/api/stock-adjustments").with(as("STOCK_WRITE")).contentType(APPLICATION_JSON)
                        .content("{\"branchId\":1,\"packId\":5,\"quantity\":-2,\"reason\":\"rotura\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/stock-adjustments/9")))
                .andExpect(jsonPath("$.quantity").value(-2))
                .andExpect(jsonPath("$.reason").value("rotura"));
        verify(adjustments).create(argThat(r -> r.branchId() == 1L && r.productionId() == null
                && r.quantity() == -2));
    }

    @Test
    void writesRequireStockWrite() throws Exception {
        mvc.perform(post("/api/transfers").with(as("STOCK_READ")).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/transfers/7").with(as("STOCK_READ"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/stock-adjustments").with(as("STOCK_READ")).contentType(APPLICATION_JSON)
                .content("{}")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/stock-adjustments/9").with(as("STOCK_READ"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/transfers").with(as("STOCK_WRITE"))).andExpect(status().isForbidden());
    }

    @Test
    void deleteAdjustmentAnswers204() throws Exception {
        mvc.perform(delete("/api/stock-adjustments/9").with(as("STOCK_WRITE"))).andExpect(status().isNoContent());
        verify(adjustments).annul(9L, 5L);
    }

    @Test
    void createAdjustmentAcceptsTextIdsAndBlankProduction() throws Exception {
        given(adjustments.create(any())).willReturn(new StockMovement(9L, LocalDate.of(2026, 11, 3), 1, 4, 5, 2,
                MovementType.ADJUSTMENT, null, "x"));
        mvc.perform(post("/api/stock-adjustments").with(as("STOCK_WRITE")).contentType(APPLICATION_JSON)
                        .content("{\"branchId\":\"1\",\"packId\":\"5\",\"productionId\":\"\","
                                + "\"quantity\":2,\"reason\":\"x\"}"))
                .andExpect(status().isCreated());
        verify(adjustments).create(argThat(r -> r.branchId() == 1L && r.packId() == 5L && r.productionId() == null));
    }
}
