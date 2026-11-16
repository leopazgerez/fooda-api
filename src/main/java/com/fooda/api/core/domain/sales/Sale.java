package com.fooda.api.core.domain.sales;

import com.fooda.api.core.domain.expense.PaymentMethod;
import com.fooda.api.shared.domain.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record Sale(Long id, LocalDate date, long branchId, Long customerId, SaleType type, String reason,
                   PaymentMethod method, BigDecimal discount, BigDecimal shipping, String notes, List<SaleLine> lines) {

    public Sale {
        discount = discount == null ? BigDecimal.ZERO : discount;
        shipping = shipping == null ? BigDecimal.ZERO : shipping;
        if (discount.signum() < 0 || shipping.signum() < 0) {
            throw new ValidationException("discount", "Descuento y envío deben ser ≥ 0");
        }
        lines = List.copyOf(lines);
    }
}
