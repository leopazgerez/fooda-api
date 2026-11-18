package com.fooda.api.finance.application.service;

import com.fooda.api.catalog.fakes.*;
import com.fooda.api.core.domain.catalog.*;
import com.fooda.api.core.domain.expense.Expense;
import com.fooda.api.core.domain.expense.PaymentMethod;
import com.fooda.api.core.domain.finance.Finance;
import com.fooda.api.core.domain.finance.MonthClose;
import com.fooda.api.core.domain.finance.SaleProfits;
import com.fooda.api.core.domain.production.*;
import com.fooda.api.core.domain.sales.Payment;
import com.fooda.api.core.domain.sales.Sale;
import com.fooda.api.core.domain.sales.SaleLine;
import com.fooda.api.core.domain.sales.SaleType;
import com.fooda.api.finance.application.port.in.FinanceUseCase;
import com.fooda.api.finance.application.port.in.MonthlyView;
import com.fooda.api.production.fakes.*;
import com.fooda.api.sales.fakes.InMemoryPaymentRepository;
import com.fooda.api.sales.fakes.InMemorySaleRepository;
import com.fooda.api.shared.DirectTransactions;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.shared.application.exception.ResourceNotFoundException;
import com.fooda.api.shared.domain.ConflictException;
import com.fooda.api.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinanceServiceTest {

    private static final LocalDate OCT = LocalDate.of(2026, 10, 10);
    private static final YearMonth M_OCT = YearMonth.of(2026, 10);
    private static final YearMonth M_NOV = YearMonth.of(2026, 11);
    private static final long PACK = 7L;

    private final InMemoryProductionRepository productions = new InMemoryProductionRepository();
    private final InMemorySaleRepository sales = new InMemorySaleRepository();
    private final InMemoryPaymentRepository payments = new InMemoryPaymentRepository();
    private final InMemoryExpenseRepository expenses = new InMemoryExpenseRepository();
    private final InMemorySettingsRepository settings = new InMemorySettingsRepository();
    private final InMemoryReserveFundRepository funds = new InMemoryReserveFundRepository();
    private final InMemorySupplyRepository supplies = new InMemorySupplyRepository();
    private final InMemoryBranchRepository branches = new InMemoryBranchRepository();
    private final FixedMonthCloses closes = new FixedMonthCloses();
    private final NoopStockLock lock = new NoopStockLock();
    private final FinanceUseCase service = new FinanceService(productions, sales, payments, expenses, settings,
            funds, supplies, branches, closes, lock,
            new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), ZoneOffset.UTC)),
            new DirectTransactions());

    private final Settings st = settings(new BigDecimal("45000"));
    private final long branch;
    private final long lot;

    {
        settings.save(st);
        funds.save(new ReserveFund(null, "Ahorro", new BigDecimal("20"), null));
        branch = branches.save(new Branch(null, "JVG", "", 10, 1, 1,
                new Shipping(ShippingType.AMOUNT, BigDecimal.ZERO), false, false, null)).id();
        Supply flour = new Supply(1L, "Harina", Unit.G, 1, null, BigDecimal.ONE, null, null, null, true, null);
        Dish dish = new Dish(1L, "Plato", List.of(new RecipeLine(1L, BigDecimal.ONE, Unit.G, BigDecimal.ONE)), null);
        var snap = new ProductionSnapshot(Map.of(1L, new BigDecimal("10000")), Map.of(1L, dish),
                List.of(new SnapshotPack(PACK, 1L, List.of(new MenuSlot(1, 1L)))), st, List.of(flour));
        lot = productions.save(new Production(null, "1 OCT 26", 1, ProductionStatus.CLOSED, OCT, OCT,
                List.of(new ProductionLine(PACK, 50, 50)), snap, Map.of(1L, new BigDecimal("14000")), null, null))
                .id();
    }

    private static Settings settings(BigDecimal packPrice) {
        return new Settings(packPrice, new BigDecimal("20"), 4L, 1, 1, new BigDecimal("24000"), 3, 30,
                BigDecimal.TEN, List.of(), List.of(Settings.FIXED_EXPENSE_TYPE), null);
    }

    private Sale sale(LocalDate date, int qty) {
        return sales.add(new Sale(null, date, branch, null, SaleType.RETAIL, null, PaymentMethod.CASH, null, null,
                null, List.of(new SaleLine(lot, PACK, qty, new BigDecimal("45000")))));
    }

    private void fixedExpense(LocalDate date, String amount) {
        expenses.add(new Expense(null, date, Settings.FIXED_EXPENSE_TYPE, "alquiler", new BigDecimal(amount), null,
                null, PaymentMethod.CASH, true, null, null, null, null));
    }

    @Test
    void monthlyListsMonthsWithDataPlusTodayDescendingUpToTwelve() {
        for (int i = 0; i < 14; i++) {
            sale(LocalDate.of(2025, 9, 1).plusMonths(i), 1);
        }
        var months = service.monthly();
        assertThat(months).hasSize(12);
        assertThat(months.get(0).report().month()).isEqualTo(M_NOV);
        assertThat(months.get(1).report().month()).isEqualTo(M_OCT);
        assertThat(months.get(11).report().month()).isEqualTo(YearMonth.of(2025, 12));
    }

    @Test
    void openMonthIsLiveAndClosedMonthReadsTheSnapshot() {
        sale(OCT, 2);
        sale(LocalDate.of(2026, 11, 3), 1);
        funds.save(new ReserveFund(null, "Otro", new BigDecimal("5"), null));
        MonthlyView closed = service.close("2026-10");
        var novBefore = service.month(M_NOV).report().profitInPacks();
        long flourId = supplies.save(new Supply(null, "Harina", Unit.G, 1, null, BigDecimal.ONE, null, null, null,
                true, null)).id();

        settings.save(settings(new BigDecimal("90000")));
        supplies.addPrice(new SupplyPrice(null, flourId, OCT, new BigDecimal("99999")));
        funds.findAll().forEach(f -> funds.save(new ReserveFund(f.id(), f.name(), new BigDecimal("50"), f.version())));

        assertThat(service.month(M_OCT)).isEqualTo(closed);
        assertThat(service.month(M_NOV).closed()).isFalse();
        assertThat(service.month(M_NOV).report().reserves().get(0).percentage()).isEqualByComparingTo("50");
        assertThat(service.month(M_NOV).report().profitInPacks()).isNotEqualTo(novBefore);
    }

    @Test
    void frozenOverheadIsRoundedToFourDecimalsAndMatchesSnapshot() {
        fixedExpense(OCT, "50000");
        // 50 + 2 packs: 50000 / 52 = 961.538461...
        productions.save(new Production(null, "2 OCT 26", 2, ProductionStatus.CLOSED, OCT, OCT,
                List.of(new ProductionLine(PACK, 2, 2)), productions.findById(lot).orElseThrow().snapshot(),
                Map.of(1L, new BigDecimal("14000")), null, null));
        MonthlyView v = service.close("2026-10");
        BigDecimal frozen = closes.closes.get(M_OCT).overheadPerPack();
        assertThat(frozen).isEqualTo(new BigDecimal("961.5385"));
        assertThat(frozen.scale()).isEqualTo(4);
        assertThat(v.report().overhead().value()).isEqualTo(frozen);
    }

    @Test
    void closeFreezesOverheadAndReportIsNotProvisional() {
        fixedExpense(OCT, "50000");
        MonthlyView v = service.close("2026-10");
        assertThat(v.closed()).isTrue();
        assertThat(v.closedOn()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(closes.closes.get(M_OCT).overheadPerPack()).isEqualByComparingTo("1000");
        assertThat(v.report().overhead().provisional()).isFalse();
        assertThat(v.report().overhead().value()).isEqualByComparingTo("1000");
    }

    @Test
    void closeValidations() {
        for (String bad : new String[]{"2026-1", "2026-13", null, "abc"}) {
            assertThatThrownBy(() -> service.close(bad)).isInstanceOf(ValidationException.class)
                    .hasFieldOrPropertyWithValue("field", "month");
        }
        assertThatThrownBy(() -> service.close("2026-12")).isInstanceOf(ValidationException.class)
                .hasMessage("No se puede cerrar un mes futuro");
        service.close("2026-10");
        assertThatThrownBy(() -> service.close("2026-10")).isInstanceOf(ConflictException.class)
                .hasMessage("Ese mes ya está cerrado");
    }

    @Test
    void reopenSoftDeletesAndAllowsClosingAgain() {
        service.close("2026-10");
        service.reopen("2026-10", 1L);
        assertThat(service.month(M_OCT).closed()).isFalse();
        assertThatThrownBy(() -> service.reopen("2026-10", 1L)).isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Ese mes no está cerrado");
        assertThat(service.close("2026-10").closed()).isTrue();
    }

    @Test
    void closeAndReopenTakeTheStockLock() {
        service.close("2026-10");
        service.reopen("2026-10", 1L);
        assertThat(lock.calls).isEqualTo(2);
    }

    @Test
    void profitAddsEverySaleOfTheMonth() {
        Sale a = sale(OCT, 2);
        Sale b = sale(OCT.plusDays(3), 3);
        sale(OCT.minusMonths(1), 4);
        var profits = new SaleProfits(productions.findAll(), supplies.findAll(), expenses.findAll(), m -> Optional.empty(), st,
                branches.findAll());
        assertThat(service.month(M_OCT).report().profit())
                .isEqualByComparingTo(profits.of(a).add(profits.of(b)));
    }

    // ---- characterization: the bounded load must match the old full load ----

    /** The pre-performance {@code FinanceService.view(load(), month)}: every row of every repository. */
    private MonthlyView fullView(YearMonth month) {
        Map<YearMonth, MonthClose> cl = new HashMap<>();
        closes.findAll().forEach(c -> cl.put(c.month(), c));
        MonthClose c = cl.get(month);
        if (c != null) {
            return new MonthlyView(c.report(), c.closedOn());
        }
        return new MonthlyView(fullReport(month, cl, Optional.empty()), null);
    }

    private com.fooda.api.core.domain.finance.MonthlyReport fullReport(YearMonth month, Map<YearMonth, MonthClose> cl,
                                                                       Optional<MonthClose> close) {
        Settings s = settings.require();
        var prods = productions.findAll();
        var exps = expenses.findAll();
        var profits = new SaleProfits(prods, supplies.findAll(), exps, m -> Optional.ofNullable(cl.get(m)), s,
                branches.findAll());
        var all = sales.findAll();
        BigDecimal profit = all.stream().filter(x -> YearMonth.from(x.date()).equals(month)).map(profits::of)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Finance.report(month, prods, all, payments.findAll(), exps, close, s, funds.findAll(), profit);
    }

    private List<MonthlyView> fullMonthly() {
        TreeSet<YearMonth> months = new TreeSet<>();
        sales.findAll().forEach(s -> months.add(YearMonth.from(s.date())));
        expenses.findAll().forEach(e -> months.add(YearMonth.from(e.date())));
        payments.findAll().forEach(p -> months.add(YearMonth.from(p.date())));
        productions.findAll().forEach(p -> months.add(YearMonth.from(p.closedOn() != null ? p.closedOn() : p.start())));
        closes.findAll().forEach(c -> months.add(c.month()));
        months.add(M_NOV);
        return months.descendingSet().stream().limit(12).map(this::fullView).toList();
    }

    /** The pre-performance {@code close}: overhead and report from the full load, draft visible to profits. */
    private MonthlyView fullClose(YearMonth month) {
        BigDecimal overhead = Finance.overheadOfMonth(month, expenses.findAll(), productions.findAll());
        overhead = overhead == null ? null : overhead.setScale(4, RoundingMode.HALF_UP);
        MonthClose draft = new MonthClose(month, overhead, LocalDate.of(2026, 11, 15));
        Map<YearMonth, MonthClose> cl = new HashMap<>();
        closes.findAll().forEach(c -> cl.put(c.month(), c));
        cl.put(month, draft);
        return new MonthlyView(fullReport(month, cl, Optional.of(draft)), LocalDate.of(2026, 11, 15));
    }

    private void assertSameAsFullLoad() {
        assertThat(service.monthly()).isEqualTo(fullMonthly());
        for (YearMonth m = YearMonth.of(2024, 12); !m.isAfter(YearMonth.of(2027, 1)); m = m.plusMonths(1)) {
            assertThat(service.month(m)).as(m.toString()).isEqualTo(fullView(m));
        }
    }

    @Test
    void boundedLoadGivesTheSameReportsAsTheFullLoad() {
        var snap = productions.findById(lot).orElseThrow().snapshot();
        LocalDate sep = LocalDate.of(2026, 9, 20);
        long sepLot = productions.save(new Production(null, "1 SEP 26", 1, ProductionStatus.CLOSED, sep, sep,
                List.of(new ProductionLine(PACK, 30, 30)), snap, Map.of(1L, new BigDecimal("9000")), null, null)).id();
        long augLot = productions.save(new Production(null, "1 AGO 26", 1, ProductionStatus.CLOSED,
                LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 5), List.of(new ProductionLine(PACK, 20, 20)), snap,
                Map.of(1L, new BigDecimal("7000")), null, null)).id();
        // another lot closed in September: part of September's packs, never sold
        productions.save(new Production(null, "2 SEP 26", 2, ProductionStatus.CLOSED, sep, sep.plusDays(2),
                List.of(new ProductionLine(PACK, 10, 10)), snap, Map.of(1L, new BigDecimal("3000")), null, null));
        productions.save(new Production(null, "1 NOV 26", 1, ProductionStatus.PLANNED, LocalDate.of(2026, 11, 2),
                null, List.of(new ProductionLine(PACK, 40, 0)), snap, Map.of(), null, null));

        fixedExpense(sep, "60000");
        fixedExpense(LocalDate.of(2026, 8, 10), "20000");
        fixedExpense(OCT, "50000");
        fixedExpense(LocalDate.of(2026, 12, 1), "999"); // future month
        for (int i = 0; i < 20; i++) { // 2025-01 .. 2026-08: most fall outside monthly()'s 12 months
            fixedExpense(LocalDate.of(2025, 1, 15).plusMonths(i), String.valueOf(1000 + i));
        }

        Sale aug = sales.add(new Sale(null, LocalDate.of(2026, 8, 20), branch, null, SaleType.RETAIL, null,
                PaymentMethod.CASH, null, null, null, List.of(new SaleLine(augLot, PACK, 3, new BigDecimal("45000")))));
        Sale sepSale = sales.add(new Sale(null, sep.plusDays(5), branch, null, SaleType.RETAIL, null,
                PaymentMethod.CASH, null, null, null, List.of(new SaleLine(sepLot, PACK, 4, new BigDecimal("45000")))));
        // October sale of the September lot: its overhead is September's
        Sale octOfSep = sales.add(new Sale(null, OCT.plusDays(2), branch, null, SaleType.RETAIL, null,
                PaymentMethod.CASH, null, null, null, List.of(new SaleLine(sepLot, PACK, 5, new BigDecimal("45000")),
                new SaleLine(lot, PACK, 1, new BigDecimal("45000")))));
        sale(OCT, 2);
        sale(LocalDate.of(2026, 11, 3), 1);
        sales.add(new Sale(null, LocalDate.of(2026, 11, 4), branch, null, SaleType.RETAIL, null, PaymentMethod.CASH,
                null, null, null, List.of(new SaleLine(augLot, PACK, 1, new BigDecimal("45000")))));

        payments.add(new Payment(null, aug.id(), LocalDate.of(2026, 8, 21), new BigDecimal("100000"), PaymentMethod.CASH));
        payments.add(new Payment(null, sepSale.id(), OCT.plusDays(1), new BigDecimal("180000"), PaymentMethod.CASH));
        payments.add(new Payment(null, octOfSep.id(), LocalDate.of(2026, 11, 1), new BigDecimal("50000"), PaymentMethod.CASH));

        assertSameAsFullLoad();

        MonthlyView expectedAug = fullClose(YearMonth.of(2026, 8));
        assertThat(service.close("2026-08")).isEqualTo(expectedAug);
        assertSameAsFullLoad();

        MonthlyView expectedSep = fullClose(YearMonth.of(2026, 9));
        assertThat(service.close("2026-09")).isEqualTo(expectedSep);
        assertSameAsFullLoad();

        MonthlyView expectedOct = fullClose(M_OCT);
        assertThat(service.close("2026-10")).isEqualTo(expectedOct);
        assertSameAsFullLoad();
    }
}
