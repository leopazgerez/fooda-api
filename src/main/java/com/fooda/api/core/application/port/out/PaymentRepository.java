package com.fooda.api.core.application.port.out;

import com.fooda.api.core.domain.sales.Payment;

import java.util.List;

public interface PaymentRepository {

    Payment add(Payment payment);

    List<Payment> findBySale(long saleId);

    List<Payment> findAll();

    void softDeleteBySale(long saleId, long actorId);
}
