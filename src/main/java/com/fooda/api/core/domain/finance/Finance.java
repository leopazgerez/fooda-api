package com.fooda.api.core.domain.finance;

import com.fooda.api.core.domain.catalog.Settings;
import com.fooda.api.core.domain.expense.Expense;
import com.fooda.api.core.domain.expense.PaymentMethod;
import com.fooda.api.core.domain.production.Production;
import com.fooda.api.core.domain.production.ProductionStatus;
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

    /** Gastos fijos pagados del mes (no con packs) ÷ packs reales cerrados ese mes; sin packs, null. */
    public static BigDecimal overheadOfMonth(YearMonth month, List<Expense> expenses, List<Production> productions) {
        int packs = productions.stream()
                .filter(p -> p.status() == ProductionStatus.CLOSED && inMonth(p.closedOn(), month))
                .mapToInt(Production::actualPacks).sum();
        if (packs == 0) {
            return null;
        }
        BigDecimal fixed = expenses.stream()
                .filter(e -> e.paid() && e.method() != PaymentMethod.PACKS
                        && Settings.FIXED_EXPENSE_TYPE.equals(e.type()) && inMonth(e.date(), month))
                .map(Expense::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
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
}
