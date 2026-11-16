package com.fooda.api.finance.application.service;

import com.fooda.api.core.application.port.out.BranchRepository;
import com.fooda.api.core.application.port.out.ExpenseRepository;
import com.fooda.api.core.application.port.out.MonthCloseRepository;
import com.fooda.api.core.application.port.out.PaymentRepository;
import com.fooda.api.core.application.port.out.ProductionRepository;
import com.fooda.api.core.application.port.out.ReserveFundRepository;
import com.fooda.api.core.application.port.out.SaleRepository;
import com.fooda.api.core.application.port.out.SettingsRepository;
import com.fooda.api.core.application.port.out.StockLock;
import com.fooda.api.core.application.port.out.SupplyRepository;
import com.fooda.api.core.domain.catalog.ReserveFund;
import com.fooda.api.core.domain.catalog.Settings;
import com.fooda.api.core.domain.expense.Expense;
import com.fooda.api.core.domain.finance.Finance;
import com.fooda.api.core.domain.finance.MonthClose;
import com.fooda.api.core.domain.finance.MonthlyReport;
import com.fooda.api.core.domain.finance.SaleProfits;
import com.fooda.api.core.domain.production.Production;
import com.fooda.api.core.domain.sales.Payment;
import com.fooda.api.core.domain.sales.Sale;
import com.fooda.api.finance.application.port.in.FinanceUseCase;
import com.fooda.api.finance.application.port.in.MonthlyView;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.shared.application.exception.ResourceNotFoundException;
import com.fooda.api.shared.application.port.out.Transactions;
import com.fooda.api.shared.domain.ConflictException;
import com.fooda.api.shared.domain.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Stream;

public class FinanceService implements FinanceUseCase {

    private static final int MAX_MONTHS = 12;

    private final ProductionRepository productions;
    private final SaleRepository sales;
    private final PaymentRepository payments;
    private final ExpenseRepository expenses;
    private final SettingsRepository settings;
    private final ReserveFundRepository funds;
    private final SupplyRepository supplies;
    private final BranchRepository branches;
    private final MonthCloseRepository monthCloses;
    private final StockLock stockLock;
    private final BusinessCalendar calendar;
    private final Transactions tx;

    public FinanceService(ProductionRepository productions, SaleRepository sales, PaymentRepository payments,
                          ExpenseRepository expenses, SettingsRepository settings, ReserveFundRepository funds,
                          SupplyRepository supplies, BranchRepository branches, MonthCloseRepository monthCloses,
                          StockLock stockLock, BusinessCalendar calendar, Transactions tx) {
        this.productions = productions;
        this.sales = sales;
        this.payments = payments;
        this.expenses = expenses;
        this.settings = settings;
        this.funds = funds;
        this.supplies = supplies;
        this.branches = branches;
        this.monthCloses = monthCloses;
        this.stockLock = stockLock;
        this.calendar = calendar;
        this.tx = tx;
    }

    /** Everything a month report reads, loaded once. */
    private record Data(List<Production> productions, List<Sale> sales, List<Payment> payments,
                        List<Expense> expenses, Settings settings, List<ReserveFund> funds,
                        Map<YearMonth, MonthClose> closes, SaleProfits profits) {

        MonthlyReport report(YearMonth month, Optional<MonthClose> close) {
            BigDecimal profit = sales.stream().filter(s -> YearMonth.from(s.date()).equals(month))
                    .map(profits::of).reduce(BigDecimal.ZERO, BigDecimal::add);
            return Finance.report(month, productions, sales, payments, expenses, close, settings, funds, profit);
        }
    }

    /** {@code closes} stays mutable: close() adds its draft before any profit reads it. */
    private Data load() {
        Map<YearMonth, MonthClose> closes = new HashMap<>();
        monthCloses.findAll().forEach(c -> closes.put(c.month(), c));
        Settings s = settings.require();
        List<Production> prods = productions.findAll();
        List<Expense> exps = expenses.findAll();
        return new Data(prods, sales.findAll(), payments.findAll(), exps, s, funds.findAll(), closes,
                new SaleProfits(prods, supplies.findAll(), exps, m -> Optional.ofNullable(closes.get(m)), s,
                        branches.findAll()));
    }

    private static MonthlyView view(Data d, YearMonth month) {
        MonthClose close = d.closes().get(month);
        return close != null ? new MonthlyView(close.report(), close.closedOn())
                : new MonthlyView(d.report(month, Optional.empty()), null);
    }

    @Override
    public List<MonthlyView> monthly() {
        Data d = load();
        TreeSet<YearMonth> months = new TreeSet<>();
        d.sales().forEach(s -> months.add(YearMonth.from(s.date())));
        d.expenses().forEach(e -> months.add(YearMonth.from(e.date())));
        d.payments().forEach(p -> months.add(YearMonth.from(p.date())));
        d.productions().forEach(p -> months.add(YearMonth.from(p.closedOn() != null ? p.closedOn() : p.start())));
        months.addAll(d.closes().keySet());
        months.add(YearMonth.from(calendar.today()));
        return months.descendingSet().stream().limit(MAX_MONTHS).map(m -> view(d, m)).toList();
    }

    @Override
    public MonthlyView month(YearMonth month) {
        return view(load(), month);
    }

    @Override
    public MonthlyView close(String text) {
        YearMonth month = parse(text);
        LocalDate today = calendar.today();
        if (month.isAfter(YearMonth.from(today))) {
            throw new ValidationException("month", "No se puede cerrar un mes futuro");
        }
        return tx.get(() -> {
            stockLock.lock();
            if (monthCloses.find(month).isPresent()) {
                throw new ConflictException("month", "Ese mes ya está cerrado");
            }
            Data d = load();
            BigDecimal overhead = Finance.overheadOfMonth(month, d.expenses(), d.productions());
            overhead = overhead == null ? null : overhead.setScale(4, RoundingMode.HALF_UP);
            MonthClose draft = new MonthClose(month, overhead, today);
            d.closes().put(month, draft);
            MonthlyReport report = d.report(month, Optional.of(draft));
            monthCloses.add(new MonthClose(month, overhead, today, report));
            return new MonthlyView(report, today);
        });
    }

    @Override
    public void reopen(String text, long actorId) {
        YearMonth month = parse(text);
        tx.run(() -> {
            stockLock.lock();
            if (monthCloses.find(month).isEmpty()) {
                throw new ResourceNotFoundException("Ese mes no está cerrado");
            }
            monthCloses.softDelete(month, actorId);
        });
    }

    private static YearMonth parse(String text) {
        try {
            if (text != null && text.matches("\\d{4}-\\d{2}")) {
                return YearMonth.parse(text);
            }
        } catch (DateTimeParseException ignored) {
            // falls through to the 400
        }
        throw new ValidationException("month", "Indicá el mes (AAAA-MM)");
    }
}
