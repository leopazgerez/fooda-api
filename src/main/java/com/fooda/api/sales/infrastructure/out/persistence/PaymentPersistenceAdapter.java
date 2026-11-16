package com.fooda.api.sales.infrastructure.out.persistence;

import com.fooda.api.core.application.port.out.PaymentRepository;
import com.fooda.api.core.domain.expense.PaymentMethod;
import com.fooda.api.core.domain.sales.Payment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;

@Component
public class PaymentPersistenceAdapter implements PaymentRepository {

    private final PaymentJpaRepository repository;
    private final Clock clock;

    public PaymentPersistenceAdapter(PaymentJpaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Payment add(Payment p) {
        PaymentJpaEntity e = new PaymentJpaEntity();
        e.saleId = p.saleId();
        e.date = p.date();
        e.amount = p.amount();
        e.method = p.method().code();
        return toDomain(repository.saveAndFlush(e));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Payment> findBySale(long saleId) {
        return repository.findBySaleIdOrderById(saleId).stream().map(PaymentPersistenceAdapter::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Payment> findAll() {
        return repository.findAllByOrderById().stream().map(PaymentPersistenceAdapter::toDomain).toList();
    }

    @Override
    @Transactional
    public void softDeleteBySale(long saleId, long actorId) {
        repository.softDeleteBySale(saleId, actorId, clock.instant());
    }

    private static Payment toDomain(PaymentJpaEntity e) {
        return new Payment(e.id, e.saleId, e.date, e.amount, PaymentMethod.of(e.method, "method"));
    }
}
