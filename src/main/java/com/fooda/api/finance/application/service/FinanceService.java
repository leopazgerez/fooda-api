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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

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

    /** Everything the reports of a range of months read, loaded once. */
    private record Data(List<Production> productions, List<Sale> sales, List<Payment> payments,
                        List<Expense> expenses, Settings settings, List<ReserveFund> funds, SaleProfits profits) {

        MonthlyReport report(YearMonth month, Optional<MonthClose> close) {
            BigDecimal profit = sales.stream().filter(s -> YearMonth.from(s.date()).equals(month))
                    .map(profits::of).reduce(BigDecimal.ZERO, BigDecimal::add);
            return Finance.report(month, productions, sales, payments, expenses, close, settings, funds, profit);
        }
    }

    /** Mutable: close() adds its draft before any profit reads it. */
    private Map<YearMonth, MonthClose> closes() {
        Map<YearMonth, MonthClose> closes = new HashMap<>();
        monthCloses.findAll().forEach(c -> closes.put(c.month(), c));
        return closes;
    }

    /**
     * Sales, payments, expenses and closed lots of [from, to]; plus what their profit reads: the sold lots and,
     * for each lot month outside the range that is not closed (a close freezes it), that month's overhead inputs
     * (its expenses and lots closed in it).
     */
    private Data load(YearMonth from, YearMonth to, Map<YearMonth, MonthClose> closes) {
        Settings s = settings.require();
        List<Sale> ss = sales.findBetween(from.atDay(1), to.atEndOfMonth());
        List<Expense> exps = new ArrayList<>(expenses.findBetween(from.atDay(1), to.atEndOfMonth()));
        Map<Long, Production> prods = new TreeMap<>();
        productions.findClosedBetween(from.atDay(1), to.atEndOfMonth()).forEach(p -> prods.put(p.id(), p));
        Set<Long> lotIds = new HashSet<>();
        ss.forEach(x -> x.lines().forEach(l -> lotIds.add(l.productionId())));
        List<Production> lots = productions.findAllById(lotIds);
        lots.stream().map(p -> YearMonth.from(p.closedOn() != null ? p.closedOn() : p.start()))
                .filter(m -> (m.isBefore(from) || m.isAfter(to)) && !closes.containsKey(m)).distinct()
                .forEach(m -> {
                    exps.addAll(expenses.findBetween(m.atDay(1), m.atEndOfMonth()));
                    productions.findClosedBetween(m.atDay(1), m.atEndOfMonth()).forEach(p -> prods.put(p.id(), p));
                });
        lots.forEach(p -> prods.put(p.id(), p));
        List<Production> all = List.copyOf(prods.values());
        return new Data(all, ss, payments.findBetween(from.atDay(1), to.atEndOfMonth()), exps, s, funds.findAll(),
                new SaleProfits(all, supplies.findAll(), exps, m -> Optional.ofNullable(closes.get(m)), s,
                        branches.findAll()));
    }

    private static MonthlyView view(Data d, Map<YearMonth, MonthClose> closes, YearMonth month) {
        MonthClose close = closes.get(month);
        return close != null ? new MonthlyView(close.report(), close.closedOn())
                : new MonthlyView(d.report(month, Optional.empty()), null);
    }

    @Override
    public List<MonthlyView> monthly() {
        Map<YearMonth, MonthClose> closes = closes();
        TreeSet<YearMonth> months = new TreeSet<>(closes.keySet());
        months.addAll(sales.months());
        months.addAll(expenses.months());
        months.addAll(payments.months());
        months.addAll(productions.months());
        months.add(YearMonth.from(calendar.today()));
        List<YearMonth> shown = months.descendingSet().stream().limit(MAX_MONTHS).toList();
        List<YearMonth> open = shown.stream().filter(m -> !closes.containsKey(m)).toList();
        Data d = open.isEmpty() ? null : load(open.get(open.size() - 1), open.get(0), closes);
        return shown.stream().map(m -> view(d, closes, m)).toList();
    }

    @Override
    public MonthlyView month(YearMonth month) {
        Map<YearMonth, MonthClose> closes = closes();
        return view(closes.containsKey(month) ? null : load(month, month, closes), closes, month);
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
            Map<YearMonth, MonthClose> closes = closes();
            Data d = load(month, month, closes);
            BigDecimal overhead = Finance.overheadOfMonth(month, d.expenses(), d.productions());
            overhead = overhead == null ? null : overhead.setScale(4, RoundingMode.HALF_UP);
            MonthClose draft = new MonthClose(month, overhead, today);
            closes.put(month, draft);
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
