package com.fooda.api.core.application.port.out;

import com.fooda.api.core.domain.expense.Expense;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ExpenseRepository {

    Expense add(Expense e);

    /** Update honoring {@code version}; stale → OptimisticLockingFailureException. */
    Expense save(Expense e);

    Optional<Expense> findById(long id);

    Optional<Expense> findBySale(long saleId);

    List<Expense> findAll();

    /** Live expenses dated in [from, to], ordered like {@link #findAll()}. */
    List<Expense> findBetween(LocalDate from, LocalDate to);

    /** Months with a live expense. */
    Set<YearMonth> months();

    Optional<Expense> findByPurchase(long purchaseId);

    void softDelete(long id, long actorId);
}
