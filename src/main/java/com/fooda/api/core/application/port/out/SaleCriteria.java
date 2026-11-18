package com.fooda.api.core.application.port.out;

import com.fooda.api.core.domain.sales.SaleType;

import java.time.LocalDate;

public record SaleCriteria(Long branchId, SaleType type, Long customerId, LocalDate from, LocalDate to) {
}
