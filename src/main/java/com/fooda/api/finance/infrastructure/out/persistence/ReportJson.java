package com.fooda.api.finance.infrastructure.out.persistence;

import com.fooda.api.core.domain.finance.AppliedOverhead;
import com.fooda.api.core.domain.finance.MonthlyReport;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

/** Stored shape of month_close.report: decoupled from the domain records so a rename doesn't break saved rows. */
record ReportJson(String month, int produced, int sold, BigDecimal soldRatio, BigDecimal sales, BigDecimal payments,
                  BigDecimal paidExpenses, BigDecimal cashResult, OverheadJson overhead, BigDecimal profit,
                  BigDecimal profitInPacks, BigDecimal cashResultInPacks, List<ReserveJson> reserves) {

    record OverheadJson(BigDecimal value, boolean provisional, boolean reference) {
    }

    record ReserveJson(String name, BigDecimal percentage, BigDecimal amount) {
    }

    static ReportJson of(MonthlyReport r) {
        AppliedOverhead o = r.overhead();
        return new ReportJson(r.month().toString(), r.produced(), r.sold(), r.soldRatio(), r.sales(), r.payments(),
                r.paidExpenses(), r.cashResult(), new OverheadJson(o.value(), o.provisional(), o.reference()),
                r.profit(), r.profitInPacks(), r.cashResultInPacks(),
                r.reserves().stream().map(s -> new ReserveJson(s.name(), s.percentage(), s.amount())).toList());
    }

    MonthlyReport toDomain() {
        return new MonthlyReport(YearMonth.parse(month), produced, sold, soldRatio, sales, payments, paidExpenses,
                cashResult, new AppliedOverhead(overhead.value(), overhead.provisional(), overhead.reference()), profit,
                profitInPacks, cashResultInPacks,
                reserves.stream().map(s -> new MonthlyReport.ReserveShare(s.name(), s.percentage(), s.amount())).toList());
    }
}
