package com.fooda.api.sales.infrastructure.in.web;

import com.fooda.api.config.SecurityConfig;
import com.fooda.api.config.TimeConfig;
import com.fooda.api.core.domain.expense.Expense;
import com.fooda.api.core.domain.expense.PaymentMethod;
import com.fooda.api.core.domain.sales.Sale;
import com.fooda.api.core.domain.sales.SaleType;
import com.fooda.api.sales.application.port.in.CustomerUseCase;
import com.fooda.api.sales.application.port.in.ExpenseInput;
import com.fooda.api.sales.application.port.in.ExpenseUseCase;
import com.fooda.api.sales.application.port.in.FollowUpsView;
import com.fooda.api.sales.application.port.in.SaleRequest;
import com.fooda.api.sales.application.port.in.SaleUseCase;
import com.fooda.api.sales.application.port.in.SaleView;
import com.fooda.api.shared.infrastructure.in.web.GlobalExceptionHandler;
import com.fooda.api.user.application.port.out.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

@WebMvcTest({SalesController.class, CustomersController.class, ExpensesController.class})
@Import({SecurityConfig.class, GlobalExceptionHandler.class, TimeConfig.class})
class SalesWebTest {

    @Autowired MockMvc mvc;
    @MockitoBean SaleUseCase sales;
    @MockitoBean CustomerUseCase customers;
    @MockitoBean ExpenseUseCase expenses;
    @MockitoBean UserRepository users;

    private static RequestPostProcessor as(String authority) {
        return jwt().jwt(j -> j.subject("5")).authorities(new SimpleGrantedAuthority(authority));
    }

    private static SaleView saleView() {
        var s = new Sale(7L, LocalDate.of(2026, 11, 3), 1, null, SaleType.RETAIL, null, PaymentMethod.CASH, null,
                null, "n", List.of());
        return new SaleView(s, "A", null, 2, List.of(new SaleView.LineView(4, "L1", 5, "Caja", 2,
                new BigDecimal("10.00"))), new BigDecimal("20.00"), new BigDecimal("5.00"), List.of());
    }

    @Test
    void salesRequireSalesRead() throws Exception {
        given(sales.get(7)).willReturn(saleView());
        mvc.perform(get("/api/sales/7").with(as("SALES_WRITE"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/sales/7").with(as("SALES_READ"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("retail")).andExpect(jsonPath("$.method").value("cash"))
                .andExpect(jsonPath("$.lines[0].lot").value("L1")).andExpect(jsonPath("$.total").value(20.00));
    }

    @Test
    void createSaleParsesBodyAnswers201WithLocation() throws Exception {
        given(sales.create(any())).willReturn(saleView());
        mvc.perform(post("/api/sales").with(as("SALES_WRITE")).contentType(APPLICATION_JSON).content(
                        "{\"branchId\":\"1\",\"type\":\"retail\",\"method\":\"cash\",\"discount\":1.5,"
                                + "\"lines\":[{\"productionId\":\"4\",\"packId\":5,\"quantity\":2,\"unitPrice\":10}]}"))
                .andExpect(status().isCreated()).andExpect(header().string("Location", endsWith("/api/sales/7")));
        var c = ArgumentCaptor.forClass(SaleRequest.class);
        verify(sales).create(c.capture());
        assertEquals(1L, c.getValue().branchId());
        assertEquals("retail", c.getValue().type());
        assertEquals(new BigDecimal("1.50"), c.getValue().discount());
        assertEquals(2, c.getValue().lines().get(0).quantity());
        assertEquals(4L, c.getValue().lines().get(0).productionId());
        mvc.perform(post("/api/sales").with(as("SALES_WRITE")).contentType(APPLICATION_JSON).content(
                        "{\"branchId\":1,\"lines\":[{\"productionId\":4,\"packId\":5,\"quantity\":1.5}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void moneyIsRoundedHalfUpToTwoDecimals() throws Exception {
        given(sales.create(any())).willReturn(saleView());
        mvc.perform(post("/api/sales").with(as("SALES_WRITE")).contentType(APPLICATION_JSON).content(
                        "{\"branchId\":1,\"type\":\"retail\",\"method\":\"cash\",\"discount\":1.005,"
                                + "\"shipping\":2.004,\"lines\":[{\"productionId\":4,\"packId\":5,"
                                + "\"quantity\":2,\"unitPrice\":100.005}]}"))
                .andExpect(status().isCreated());
        var c = ArgumentCaptor.forClass(SaleRequest.class);
        verify(sales).create(c.capture());
        assertEquals(new BigDecimal("100.01"), c.getValue().lines().get(0).unitPrice());
        assertEquals(new BigDecimal("1.01"), c.getValue().discount());
        assertEquals(new BigDecimal("2.00"), c.getValue().shipping());
        given(expenses.create(any())).willReturn(new Expense(3L, LocalDate.of(2026, 11, 3), "t", "d",
                new BigDecimal("5"), null, null, PaymentMethod.CASH, true, null, null, null, 0L));
        mvc.perform(post("/api/expenses").with(as("SALES_WRITE")).contentType(APPLICATION_JSON)
                .content("{\"type\":\"t\",\"amount\":10.005}")).andExpect(status().isCreated());
        var e = ArgumentCaptor.forClass(ExpenseInput.class);
        verify(expenses).create(e.capture());
        assertEquals(new BigDecimal("10.01"), e.getValue().amount().value());
    }

    @Test
    void followUpsIsNotTakenAsAnId() throws Exception {
        given(customers.followUps()).willReturn(new FollowUpsView(List.of(), List.of()));
        mvc.perform(get("/api/customers/follow-ups").with(as("SALES_READ"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.followUps").isArray()).andExpect(jsonPath("$.birthdays").isArray());
        verify(customers).followUps();
    }

    @Test
    void expensePatchSendsOnlyPresentFields() throws Exception {
        given(expenses.update(org.mockito.ArgumentMatchers.eq(3L), any())).willReturn(new Expense(3L,
                LocalDate.of(2026, 11, 3), "t", "d", new BigDecimal("5"), null, null, PaymentMethod.CASH, true, null,
                null, null, 2L));
        mvc.perform(patch("/api/expenses/3").with(as("SALES_WRITE")).contentType(APPLICATION_JSON)
                        .content("{\"detail\":\"x\",\"paid\":false,\"version\":\"1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.method").value("cash"));
        var c = ArgumentCaptor.forClass(ExpenseInput.class);
        verify(expenses).update(org.mockito.ArgumentMatchers.eq(3L), c.capture());
        var in = c.getValue();
        assertEquals("x", in.detail().value());
        assertFalse(in.paid().value());
        assertEquals(1L, in.version().value());
        assertFalse(in.amount().present());
        assertFalse(in.date().present());
        assertTrue(in.detail().present());
        mvc.perform(patch("/api/expenses/3").with(as("SALES_WRITE")).contentType(APPLICATION_JSON)
                        .content("{\"version\":null}")).andExpect(status().isBadRequest());
    }

    @Test
    void writesRequireSalesWrite() throws Exception {
        mvc.perform(post("/api/sales").with(as("SALES_READ")).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/customers").with(as("SALES_READ")).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/expenses/1").with(as("SALES_READ")).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/customers/1").with(as("SALES_READ"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/expenses").with(as("STOCK_READ"))).andExpect(status().isForbidden());
    }

    @Test
    void deleteSaleIs204() throws Exception {
        mvc.perform(delete("/api/sales/7").with(as("SALES_WRITE"))).andExpect(status().isNoContent());
        verify(sales).annul(7L, 5L);
    }
}
