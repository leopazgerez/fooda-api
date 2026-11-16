package com.fooda.api.core.domain.sales;

import com.fooda.api.core.domain.expense.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Payment(Long id, long saleId, LocalDate date, BigDecimal amount, PaymentMethod method) {
}
