package com.fooda.api.sales.application.port.in;

import com.fooda.api.core.domain.sales.Customer;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CustomerView(Customer customer, String fullName, boolean regular, LocalDate lastPurchase, int packs,
                           BigDecimal total) {
}
