package com.fooda.api.core.domain.finance;

import com.fooda.api.core.domain.catalog.Settings;
import com.fooda.api.core.domain.expense.Expense;
import com.fooda.api.core.domain.expense.PaymentMethod;
import com.fooda.api.core.domain.production.Production;
import com.fooda.api.core.domain.production.ProductionLine;
import com.fooda.api.core.domain.production.ProductionStatus;
import com.fooda.api.core.domain.catalog.ReserveFund;
import com.fooda.api.core.domain.sales.Payment;
import com.fooda.api.core.domain.sales.Sale;
import com.fooda.api.core.domain.sales.SaleLine;
import com.fooda.api.core.domain.sales.SaleType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Port de fooda-admin/src/lib/domain/finanzas.test.ts (indirecto, recupero, enPacks). */
class FinanceTest {

    private static final YearMonth NOV = YearMonth.of(2026, 11);
    private final Settings settings = new Settings(new BigDecimal("45000"), BigDecimal.ONE, null, 1, 1,
            new BigDecimal("1000"), 3, 30, BigDecimal.TEN, List.of(), List.of("fijo", "mercadería"), 0L);

    private static Production prod(String closedOn, int... actual) {
        List<ProductionLine> lines = new java.util.ArrayList<>();
        for (int i = 0; i < actual.length; i++) {
            lines.add(new ProductionLine(i + 1, actual[i], actual[i]));
        }
        return new Production(1L, "", 1, closedOn == null ? ProductionStatus.PLANNED : ProductionStatus.CLOSED,
                LocalDate.parse("2026-11-01"), closedOn == null ? null : LocalDate.parse(closedOn), lines, null,
                null, null, 0L);
    }

    private static Expense exp(String date, String amount, String type, PaymentMethod method, boolean paid) {
        return new Expense(null, LocalDate.parse(date), type, "x", new BigDecimal(amount), null, null, method, paid,
                null, null, null, 0L);
    }

    private static Expense fixed(String date, String amount) {
        return exp(date, amount, "fijo", PaymentMethod.TRANSFER, true);
    }

    @Test
    void overheadIsPaidFixedExpensesOverClosedPacks() {
        List<Expense> expenses = List.of(fixed("2026-11-02", "30000"), fixed("2026-11-05", "20000"));
        assertThat(Finance.overheadOfMonth(NOV, expenses,
                List.of(prod("2026-11-04", 10, 10, 10, 10, 10), prod(null, 99))))
                .isEqualByComparingTo("1000");
    }

    @Test
    void overheadIgnoresNonFixedUnpaidAndPacksExpenses() {
        List<Expense> expenses = List.of(fixed("2026-11-02", "50000"),
                exp("2026-11-06", "99", "fijo", PaymentMethod.TRANSFER, false),
                exp("2026-11-07", "99", "mercadería", PaymentMethod.TRANSFER, true),
                exp("2026-11-08", "99", "fijo", PaymentMethod.PACKS, true), fixed("2026-10-30", "99"));
        assertThat(Finance.overheadOfMonth(NOV, expenses, List.of(prod("2026-11-04", 50))))
                .isEqualByComparingTo("1000");
    }

    @Test
    void overheadWithoutClosedPacksIsReference() {
        YearMonth dec = YearMonth.of(2026, 12);
        List<Expense> expenses = List.of(fixed("2026-12-01", "5000"));
        assertThat(Finance.overheadOfMonth(dec, expenses, List.of())).isNull();
        AppliedOverhead a = Finance.applicable(dec, Optional.empty(), expenses, List.of(), settings);
        assertThat(a.value()).isEqualByComparingTo("1000");
        assertThat(a.reference()).isTrue();
        assertThat(a.provisional()).isTrue();
    }

    @Test
    void openMonthIsProvisional() {
        AppliedOverhead a = Finance.applicable(YearMonth.of(2026, 10), Optional.empty(),
                List.of(fixed("2026-10-02", "50000")), List.of(prod("2026-10-06", 50)), settings);
        assertThat(a.value()).isEqualByComparingTo("1000");
        assertThat(a.provisional()).isTrue();
        assertThat(a.reference()).isFalse();
    }

    @Test
    void closedMonthUsesFrozenValue() {
        MonthClose close = new MonthClose(YearMonth.of(2026, 10), new BigDecimal("1234"), LocalDate.parse("2026-11-01"));
        AppliedOverhead a = Finance.applicable(YearMonth.of(2026, 10), Optional.of(close),
                List.of(fixed("2026-10-02", "999999")), List.of(prod("2026-10-06", 50)), settings);
        assertThat(a.value()).isEqualByComparingTo("1234");
        assertThat(a.provisional()).isFalse();
        assertThat(a.reference()).isFalse();
    }

    @Test
    void closedMonthWithNullFrozenUsesReference() {
        MonthClose close = new MonthClose(YearMonth.of(2026, 10), null, LocalDate.parse("2026-11-01"));
        AppliedOverhead a = Finance.applicable(YearMonth.of(2026, 10), Optional.of(close),
                List.of(fixed("2026-10-02", "30000")), List.of(prod("2026-10-06", 50)), settings);
        assertThat(a.value()).isEqualByComparingTo("1000");
        assertThat(a.reference()).isTrue();
        assertThat(a.provisional()).isFalse();
    }

    @Test
    void paybackNeededPacksToCoverCost() {
        assertThat(Finance.payback(new BigDecimal("1068000"), new BigDecimal("45000"), 10)).isEqualTo(new Payback(24, 14));
        assertThat(Finance.payback(new BigDecimal("1068000"), new BigDecimal("45000"), 30)).isEqualTo(new Payback(24, 0));
        assertThat(Finance.payback(new BigDecimal("100"), BigDecimal.ZERO, 3)).isEqualTo(new Payback(0, 0));
    }

    @Test
    void inPacksRoundsToOneDecimal() {
        assertThat(Finance.inPacks(new BigDecimal("67500"), new BigDecimal("45000"))).isEqualByComparingTo("1.5");
        assertThat(Finance.inPacks(new BigDecimal("100"), BigDecimal.ZERO)).isEqualByComparingTo("0");
    }

    private static Sale sale(String date, SaleType type, int qty) {
        return new Sale(null, LocalDate.parse(date), 1, null, type, null, null, null, null, null,
                List.of(new SaleLine(1, 1, qty, new BigDecimal("45000"))));
    }

    private static Payment pay(String date, String amount) {
        return new Payment(null, 1, LocalDate.parse(date), new BigDecimal(amount), PaymentMethod.TRANSFER);
    }

    private MonthlyReport report(YearMonth m, List<Production> prods, List<Sale> sales, List<Payment> pays,
                                 List<Expense> exps, Optional<MonthClose> close, String profit) {
        return Finance.report(m, prods, sales, pays, exps, close, settings, List.of(), new BigDecimal(profit));
    }

    @Test
    void reportCountsPaymentsByPaymentDateAndPaidWithPacksDoesNotTouchCash() {
        MonthlyReport r = report(NOV, List.of(prod("2026-11-04", 10)),
                List.of(sale("2026-11-03", SaleType.RETAIL, 2), sale("2026-10-28", SaleType.RETAIL, 1),
                        sale("2026-11-04", SaleType.PAID_WITH_PACKS, 3)),
                List.of(pay("2026-11-03", "50000"), pay("2026-11-02", "90000")),
                List.of(fixed("2026-11-02", "30000"), exp("2026-11-04", "90000", "mercadería", PaymentMethod.PACKS, true)),
                Optional.empty(), "1000");
        assertThat(r.produced()).isEqualTo(10);
        assertThat(r.sold()).isEqualTo(2);
        assertThat(r.soldRatio()).isEqualByComparingTo("0.2");
        assertThat(r.sales()).isEqualByComparingTo("90000");
        assertThat(r.payments()).isEqualByComparingTo("140000");
        assertThat(r.paidExpenses()).isEqualByComparingTo("30000");
        assertThat(r.cashResult()).isEqualByComparingTo("110000");
        assertThat(r.profit()).isEqualByComparingTo("1000");
    }

    @Test
    void reportSoldRatioIsZeroWithoutProduction() {
        MonthlyReport r = report(YearMonth.of(2027, 1), List.of(), List.of(), List.of(), List.of(), Optional.empty(), "0");
        assertThat(r.soldRatio()).isEqualByComparingTo("0");
        assertThat(r.produced()).isZero();
    }

    @Test
    void reportInPacksUsesSettingsPackPrice() {
        MonthlyReport r = report(NOV, List.of(), List.of(), List.of(pay("2026-11-03", "67500")), List.of(),
                Optional.empty(), "67500");
        assertThat(r.profitInPacks()).isEqualByComparingTo("1.5");
        assertThat(r.cashResultInPacks()).isEqualByComparingTo("1.5");
    }

    @Test
    void reserveSharesSplitPositiveProfitOnly() {
        List<ReserveFund> funds = List.of(new ReserveFund(1L, "Ahorro", new BigDecimal("20"), 0L),
                new ReserveFund(2L, "Envases", BigDecimal.TEN, 0L));
        var pos = Finance.reserveShares(new BigDecimal("100000"), funds);
        assertThat(pos.get(0).name()).isEqualTo("Ahorro");
        assertThat(pos.get(0).percentage()).isEqualByComparingTo("20");
        assertThat(pos.get(0).amount()).isEqualByComparingTo("20000");
        assertThat(pos.get(1).amount()).isEqualByComparingTo("10000");
        var neg = Finance.reserveShares(new BigDecimal("-5"), funds);
        assertThat(neg.get(0).amount()).isEqualByComparingTo("0");
        assertThat(neg.get(1).amount()).isEqualByComparingTo("0");
    }

    @Test
    void reportOverheadIsFrozenWhenTheMonthHasAClose() {
        MonthClose close = new MonthClose(NOV, new BigDecimal("800"), LocalDate.parse("2026-12-01"));
        AppliedOverhead o = report(NOV, List.of(), List.of(), List.of(), List.of(), Optional.of(close), "0").overhead();
        assertThat(o.value()).isEqualByComparingTo("800");
        assertThat(o.provisional()).isFalse();
        assertThat(o.reference()).isFalse();
    }
}
