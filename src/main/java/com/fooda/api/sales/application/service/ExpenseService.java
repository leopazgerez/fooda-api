package com.fooda.api.sales.application.service;

import com.fooda.api.core.application.port.out.ExpenseRepository;
import com.fooda.api.core.application.port.out.MonthCloseRepository;
import com.fooda.api.core.application.port.out.SettingsRepository;
import com.fooda.api.core.application.port.out.StockLock;
import com.fooda.api.core.domain.expense.Expense;
import com.fooda.api.core.domain.expense.PaymentMethod;
import com.fooda.api.sales.application.port.in.ExpenseInput;
import com.fooda.api.sales.application.port.in.ExpenseUseCase;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.Listing;
import com.fooda.api.shared.application.Page;
import com.fooda.api.shared.application.exception.ResourceNotFoundException;
import com.fooda.api.shared.application.port.out.Transactions;
import com.fooda.api.shared.domain.ConflictException;
import com.fooda.api.shared.domain.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Map;

public class ExpenseService implements ExpenseUseCase {

    private static final Comparator<Expense> BY_ID = Comparator.comparingLong(Expense::id);
    private static final Map<String, Comparator<Expense>> SORTABLE = Map.of(
            "date", Comparator.comparing(Expense::date),
            "id", BY_ID);

    private final ExpenseRepository expenses;
    private final SettingsRepository settings;
    private final MonthCloseRepository monthCloses;
    private final StockLock stockLock;
    private final BusinessCalendar calendar;
    private final Transactions tx;

    public ExpenseService(ExpenseRepository expenses, SettingsRepository settings, MonthCloseRepository monthCloses,
                          StockLock stockLock, BusinessCalendar calendar, Transactions tx) {
        this.expenses = expenses;
        this.settings = settings;
        this.monthCloses = monthCloses;
        this.stockLock = stockLock;
        this.calendar = calendar;
        this.tx = tx;
    }

    @Override
    public Page<Expense> list(ListQuery q) {
        return Listing.page(expenses.findAll(), q, SORTABLE, "date", "desc", BY_ID);
    }

    @Override
    public Expense get(long id) {
        return expenses.findById(id).orElseThrow(() -> new ResourceNotFoundException("Gasto no encontrado"));
    }

    @Override
    public Expense create(ExpenseInput in) {
        return tx.get(() -> {
            stockLock.lock(); // serializes with month close/reopen
            LocalDate date = calendar.orToday(in.date().or(null));
            monthCloses.requireOpen(date);
            BigDecimal amount = in.amount().or(null);
            BigDecimal packs = in.amountPacks().or(null);
            if (amount == null && packs == null) {
                throw new ValidationException("amount", "Indicá el monto en pesos o en packs");
            }
            BigDecimal packPrice = null;
            if (packs != null) {
                requireOneAmount(amount);
                packs = roundPacks(packs);
                packPrice = settings.require().packPrice();
                amount = packs.multiply(packPrice).setScale(2, RoundingMode.HALF_UP);
            }
            Boolean paid = in.paid().or(null);
            return expenses.add(new Expense(null, date, in.type().or(null), in.detail().or(null), amount, packs,
                    packPrice, method(in.method().or(null), PaymentMethod.TRANSFER), paid == null || paid,
                    in.notes().or(null), null, null, null));
        });
    }

    @Override
    public Expense update(long id, ExpenseInput in) {
        return tx.get(() -> {
            stockLock.lock(); // serializes with month close/reopen
            Expense e = get(id);
            requireManual(e);
            monthCloses.requireOpen(e.date());
            LocalDate date = in.date().or(e.date());
            if (date == null) {
                date = e.date();
            }
            if (!date.equals(e.date())) {
                monthCloses.requireOpen(date);
            }

            BigDecimal amount = e.amount();
            BigDecimal packs = e.amountPacks();
            BigDecimal packPrice = e.packPrice();
            BigDecimal newAmount = in.amount().or(null);
            BigDecimal newPacks = in.amountPacks().or(null);
            if (newPacks != null) {
                requireOneAmount(newAmount);
                packs = roundPacks(newPacks);
                if (packPrice == null) {
                    packPrice = settings.require().packPrice();
                }
                amount = packs.multiply(packPrice).setScale(2, RoundingMode.HALF_UP);
            } else if (newAmount != null) {
                amount = newAmount;
                packs = null;
                packPrice = null;
            }
            Boolean paid = in.paid().or(e.paid());
            Long version = in.version().or(e.version());
            return expenses.save(new Expense(e.id(), date, in.type().or(e.type()), in.detail().or(e.detail()),
                    amount, packs, packPrice, method(in.method().or(null), e.method()), paid == null ? e.paid() : paid,
                    in.notes().or(e.notes()), e.purchaseId(), e.saleId(), version == null ? e.version() : version));
        });
    }

    @Override
    public void delete(long id, long actorId) {
        tx.run(() -> {
            stockLock.lock(); // serializes with month close/reopen
            Expense e = get(id);
            requireManual(e);
            monthCloses.requireOpen(e.date());
            expenses.softDelete(id, actorId);
        });
    }

    private static void requireManual(Expense e) {
        if (e.purchaseId() != null || e.saleId() != null) {
            throw new ConflictException("origin", "Este gasto se generó automáticamente");
        }
    }

    private static void requireOneAmount(BigDecimal amount) {
        if (amount != null) {
            throw new ValidationException("amount", "Indicá el monto en pesos o en packs, no los dos");
        }
    }

    private static BigDecimal roundPacks(BigDecimal packs) {
        BigDecimal rounded = packs.setScale(2, RoundingMode.HALF_UP);
        if (rounded.signum() <= 0) {
            throw new ValidationException("amountPacks", "Los packs deben ser un número mayor a cero");
        }
        return rounded;
    }

    private static PaymentMethod method(String code, PaymentMethod fallback) {
        if (code == null) {
            return fallback;
        }
        return PaymentMethod.of(code, "method");
    }
}
