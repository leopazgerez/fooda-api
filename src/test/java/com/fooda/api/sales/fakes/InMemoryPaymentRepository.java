package com.fooda.api.sales.fakes;

import com.fooda.api.core.application.port.out.PaymentRepository;
import com.fooda.api.core.domain.sales.Payment;

import java.util.*;

public class InMemoryPaymentRepository implements PaymentRepository {

    private final Map<Long, Payment> rows = new LinkedHashMap<>();
    private long seq;

    @Override
    public Payment add(Payment p) {
        Payment saved = new Payment(++seq, p.saleId(), p.date(), p.amount(), p.method());
        rows.put(saved.id(), saved);
        return saved;
    }

    @Override public List<Payment> findBySale(long saleId) {
        return rows.values().stream().filter(p -> p.saleId() == saleId).toList();
    }

    @Override public List<Payment> findAll() { return List.copyOf(rows.values()); }

    @Override public List<Payment> findBetween(java.time.LocalDate from, java.time.LocalDate to) {
        return rows.values().stream().filter(p -> !p.date().isBefore(from) && !p.date().isAfter(to)).toList();
    }

    @Override public Set<java.time.YearMonth> months() {
        return rows.values().stream().map(p -> java.time.YearMonth.from(p.date())).collect(java.util.stream.Collectors.toSet());
    }

    @Override public void softDeleteBySale(long saleId, long actorId) { rows.values().removeIf(p -> p.saleId() == saleId); }
}
