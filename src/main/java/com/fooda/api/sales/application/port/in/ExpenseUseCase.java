package com.fooda.api.sales.application.port.in;

import com.fooda.api.core.domain.expense.Expense;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.Page;

public interface ExpenseUseCase {

    Page<Expense> list(ListQuery q);

    Expense get(long id);

    Expense create(ExpenseInput in);

    Expense update(long id, ExpenseInput in);

    void delete(long id, long actorId);
}
