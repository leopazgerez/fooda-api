package com.fooda.api.sales.application.port.in;

import java.time.LocalDate;

public record SaleFilter(Long branchId, String type, Long customerId, LocalDate from, LocalDate to) {
}
