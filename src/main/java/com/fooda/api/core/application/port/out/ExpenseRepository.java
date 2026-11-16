package com.fooda.api.core.application.port.out;

import com.fooda.api.core.domain.expense.Expense;

import java.util.List;
import java.util.Optional;

public interface ExpenseRepository {

    Expense add(Expense e);

    List<Expense> findAll();

    Optional<Expense> findByPurchase(long purchaseId);

    void softDelete(long id, long actorId);
}
