package com.fooda.api.finance.application.service;

import com.fooda.api.catalog.fakes.*;
import com.fooda.api.core.domain.catalog.*;
import com.fooda.api.core.domain.expense.Expense;
import com.fooda.api.core.domain.expense.PaymentMethod;
import com.fooda.api.core.domain.finance.SaleProfits;
import com.fooda.api.core.domain.production.*;
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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
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
}
