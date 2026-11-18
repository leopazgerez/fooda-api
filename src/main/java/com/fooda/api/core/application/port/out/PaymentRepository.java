package com.fooda.api.core.application.port.out;

import com.fooda.api.core.domain.sales.Payment;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

public interface PaymentRepository {

    Payment add(Payment payment);

    List<Payment> findBySale(long saleId);

    List<Payment> findAll();

    /** Live payments dated in [from, to], by id. */
    List<Payment> findBetween(LocalDate from, LocalDate to);

    /** Months with a live payment. */
    Set<YearMonth> months();

    void softDeleteBySale(long saleId, long actorId);
}
