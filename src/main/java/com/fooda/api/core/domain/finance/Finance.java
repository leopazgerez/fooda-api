package com.fooda.api.core.domain.finance;

import com.fooda.api.core.domain.catalog.ReserveFund;
import com.fooda.api.core.domain.catalog.Settings;
import com.fooda.api.core.domain.expense.Expense;
import com.fooda.api.core.domain.expense.PaymentMethod;
import com.fooda.api.core.domain.production.Production;
import com.fooda.api.core.domain.production.ProductionStatus;
import com.fooda.api.core.domain.sales.Payment;
import com.fooda.api.core.domain.sales.Sale;
import com.fooda.api.core.domain.sales.SaleRules;
import com.fooda.api.shared.domain.Decimals;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public final class Finance {

    private Finance() {
    }

    private static boolean inMonth(LocalDate date, YearMonth month) {
        return date != null && YearMonth.from(date).equals(month);
    }

    private static int producedPacks(YearMonth month, List<Production> productions) {
        return productions.stream()
                .filter(p -> p.status() == ProductionStatus.CLOSED && inMonth(p.closedOn(), month))
                .mapToInt(Production::actualPacks).sum();
    }

    /** Gastos pagados del mes en caja (no con packs); con {@code type} null, de cualquier tipo. */
    private static BigDecimal cashExpenses(YearMonth month, List<Expense> expenses, String type) {
        return expenses.stream()
                .filter(e -> e.paid() && e.method() != PaymentMethod.PACKS
                        && (type == null || type.equals(e.type())) && inMonth(e.date(), month))
                .map(Expense::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Gastos fijos pagados del mes (no con packs) ÷ packs reales cerrados ese mes; sin packs, null. */
    public static BigDecimal overheadOfMonth(YearMonth month, List<Expense> expenses, List<Production> productions) {
        int packs = producedPacks(month, productions);
        if (packs == 0) {
            return null;
        }
        BigDecimal fixed = cashExpenses(month, expenses, Settings.FIXED_EXPENSE_TYPE);
        return fixed.divide(BigDecimal.valueOf(packs), Decimals.MC);
    }

    /** Mes cerrado: valor congelado; abierto: provisorio; sin dato: referencia. */
    public static AppliedOverhead applicable(YearMonth month, Optional<MonthClose> close, List<Expense> expenses,
                                             List<Production> productions, Settings settings) {
        BigDecimal value = close.isPresent() ? close.get().overheadPerPack()
                : overheadOfMonth(month, expenses, productions);
        boolean reference = value == null;
        return new AppliedOverhead(reference ? settings.referenceOverhead() : value, close.isEmpty(), reference);
    }

    /** Los primeros N packs vendidos financian la producción; recién después hay ganancia. */
    public static Payback payback(BigDecimal totalCost, BigDecimal packPrice, int sold) {
        int needed = packPrice.signum() > 0
                ? totalCost.divide(packPrice, Decimals.MC).setScale(0, RoundingMode.CEILING).intValueExact() : 0;
        return new Payback(needed, Math.max(0, needed - sold));
    }

    public static BigDecimal inPacks(BigDecimal amount, BigDecimal packPrice) {
        return packPrice.signum() == 0 ? BigDecimal.ZERO
                : amount.divide(packPrice, Decimals.MC).setScale(1, RoundingMode.HALF_UP);
    }

    public static List<MonthlyReport.ReserveShare> reserveShares(BigDecimal profit, List<ReserveFund> funds) {
        return funds.stream().map(f -> new MonthlyReport.ReserveShare(f.name(), f.percentage(),
                profit.signum() > 0 ? profit.multiply(f.percentage()).divide(BigDecimal.valueOf(100), Decimals.MC)
                        : BigDecimal.ZERO)).toList();
    }

    public static MonthlyReport report(YearMonth month, List<Production> productions, List<Sale> sales,
                                       List<Payment> payments, List<Expense> expenses, Optional<MonthClose> close,
                                       Settings settings, List<ReserveFund> funds, BigDecimal profit) {
        int produced = producedPacks(month, productions);
        List<Sale> income = sales.stream()
                .filter(s -> SaleRules.isIncome(s.type()) && inMonth(s.date(), month)).toList();
        int sold = income.stream().mapToInt(s -> SaleRules.packs(s.lines())).sum();
        BigDecimal soldRatio = produced == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(sold).divide(BigDecimal.valueOf(produced), Decimals.MC);
        BigDecimal salesTotal = income.stream().map(SaleRules::total).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paid = payments.stream().filter(p -> inMonth(p.date(), month)).map(Payment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidExpenses = cashExpenses(month, expenses, null);
        BigDecimal cash = paid.subtract(paidExpenses);
        return new MonthlyReport(month, produced, sold, soldRatio, salesTotal, paid, paidExpenses, cash,
                applicable(month, close, expenses, productions, settings), profit,
                inPacks(profit, settings.packPrice()), inPacks(cash, settings.packPrice()),
                reserveShares(profit, funds));
    }
}
