package com.fooda.api.core.application.port.out;

import com.fooda.api.core.domain.expense.Expense;

import java.util.List;
import java.util.Optional;

public interface ExpenseRepository {

    Expense add(Expense e);

    /** Update honoring {@code version}; stale → OptimisticLockingFailureException. */
    Expense save(Expense e);

    Optional<Expense> findById(long id);

    Optional<Expense> findBySale(long saleId);

    List<Expense> findAll();

    Optional<Expense> findByPurchase(long purchaseId);

    void softDelete(long id, long actorId);
}
