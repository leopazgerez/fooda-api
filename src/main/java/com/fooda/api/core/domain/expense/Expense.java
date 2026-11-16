package com.fooda.api.core.domain.expense;

import com.fooda.api.shared.domain.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;

public record Expense(Long id, LocalDate date, String type, String detail, BigDecimal amount, BigDecimal amountPacks,
                      BigDecimal packPrice, PaymentMethod method, boolean paid, String notes, Long purchaseId,
                      Long saleId, Long version) {

    public Expense {
        type = type == null ? "" : type.strip();
        detail = detail == null ? "" : detail.strip();
        if (type.isEmpty()) {
            throw new ValidationException("type", "Completá tipo");
        }
        if (detail.isEmpty()) {
            throw new ValidationException("detail", "Completá detalle");
        }
        if (amount == null || amount.signum() <= 0) {
            throw new ValidationException("amount", "El monto debe ser un número mayor a cero");
        }
        if ((amountPacks == null) != (packPrice == null)) {
            throw new IllegalArgumentException("amountPacks y packPrice van juntos");
        }
    }
}
