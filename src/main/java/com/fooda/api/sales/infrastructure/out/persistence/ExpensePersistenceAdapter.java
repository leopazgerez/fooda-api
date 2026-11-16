package com.fooda.api.sales.infrastructure.out.persistence;

import com.fooda.api.core.application.port.out.ExpenseRepository;
import com.fooda.api.core.domain.expense.Expense;
import com.fooda.api.core.domain.expense.PaymentMethod;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

@Component
public class ExpensePersistenceAdapter implements ExpenseRepository {

    private final ExpenseJpaRepository expenses;
    private final Clock clock;

    public ExpensePersistenceAdapter(ExpenseJpaRepository expenses, Clock clock) {
        this.expenses = expenses;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Expense add(Expense x) {
        ExpenseJpaEntity e = new ExpenseJpaEntity();
        e.date = x.date();
        e.type = x.type();
        e.detail = x.detail();
        e.amount = x.amount();
        e.amountPacks = x.amountPacks();
        e.packPrice = x.packPrice();
        e.method = x.method().code();
        e.paid = x.paid();
        e.notes = x.notes();
        e.purchaseId = x.purchaseId();
        e.saleId = x.saleId();
        return toDomain(expenses.saveAndFlush(e));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Expense> findAll() {
        return expenses.findAll(Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id"))).stream()
                .map(ExpensePersistenceAdapter::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Expense> findByPurchase(long purchaseId) {
        return expenses.findByPurchaseId(purchaseId).map(ExpensePersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public void softDelete(long id, long actorId) {
        expenses.softDelete(id, actorId, clock.instant());
    }

    private static Expense toDomain(ExpenseJpaEntity e) {
        return new Expense(e.id, e.date, e.type, e.detail, e.amount, e.amountPacks, e.packPrice,
                PaymentMethod.of(e.method, "method"), e.paid, e.notes, e.purchaseId, e.saleId, e.getVersion());
    }
}
