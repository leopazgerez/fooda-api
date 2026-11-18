package com.fooda.api.production.fakes;

import com.fooda.api.core.application.port.out.ExpenseRepository;
import com.fooda.api.core.domain.expense.Expense;

import org.springframework.dao.OptimisticLockingFailureException;

import java.util.*;

public class InMemoryExpenseRepository implements ExpenseRepository {

    private final Map<Long, Expense> rows = new LinkedHashMap<>();
    private long seq;

    @Override
    public Expense add(Expense e) {
        var saved = new Expense(++seq, e.date(), e.type(), e.detail(), e.amount(), e.amountPacks(), e.packPrice(),
                e.method(), e.paid(), e.notes(), e.purchaseId(), e.saleId(), 0L);
        rows.put(saved.id(), saved);
        return saved;
    }

    @Override
    public Expense save(Expense e) {
        Expense old = rows.get(e.id());
        if (old == null || !Objects.equals(old.version(), e.version())) {
            throw new OptimisticLockingFailureException("stale expense " + e.id());
        }
        var saved = new Expense(e.id(), e.date(), e.type(), e.detail(), e.amount(), e.amountPacks(), e.packPrice(),
                e.method(), e.paid(), e.notes(), e.purchaseId(), e.saleId(), old.version() + 1);
        rows.put(saved.id(), saved);
        return saved;
    }

    @Override public Optional<Expense> findById(long id) { return Optional.ofNullable(rows.get(id)); }

    @Override
    public Optional<Expense> findBySale(long saleId) {
        return rows.values().stream().filter(e -> e.saleId() != null && e.saleId() == saleId).findFirst();
    }

    @Override public List<Expense> findAll() { return List.copyOf(rows.values()); }

    @Override public List<Expense> findBetween(java.time.LocalDate from, java.time.LocalDate to) {
        return rows.values().stream().filter(e -> !e.date().isBefore(from) && !e.date().isAfter(to)).toList();
    }

    @Override public Set<java.time.YearMonth> months() {
        return rows.values().stream().map(e -> java.time.YearMonth.from(e.date())).collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public Optional<Expense> findByPurchase(long purchaseId) {
        return rows.values().stream().filter(e -> e.purchaseId() != null && e.purchaseId() == purchaseId).findFirst();
    }

    @Override public void softDelete(long id, long actorId) { rows.remove(id); }
}
