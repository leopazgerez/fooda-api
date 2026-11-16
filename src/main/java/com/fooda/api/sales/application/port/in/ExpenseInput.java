package com.fooda.api.sales.application.port.in;

import com.fooda.api.shared.application.Field;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseInput(Field<LocalDate> date, Field<String> type, Field<String> detail, Field<BigDecimal> amount,
                           Field<BigDecimal> amountPacks, Field<String> method, Field<Boolean> paid,
                           Field<String> notes, Field<Long> version) {
}
