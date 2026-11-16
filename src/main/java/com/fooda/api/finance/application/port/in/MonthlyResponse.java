package com.fooda.api.finance.application.port.in;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Flat JSON shape of a month, shared by the finance endpoints and the dashboard. */
public record MonthlyResponse(String month, int produced, int sold, BigDecimal soldRatio, BigDecimal sales,
                              BigDecimal payments, BigDecimal paidExpenses, BigDecimal cashResult, boolean closed,
                              LocalDate closedOn, OverheadResponse overhead, BigDecimal profit,
                              BigDecimal profitInPacks, BigDecimal cashResultInPacks,
                              List<ReserveResponse> reserves) {

    public record OverheadResponse(BigDecimal value, boolean provisional, boolean reference) {
    }

    public record ReserveResponse(String name, BigDecimal percentage, BigDecimal amount) {
    }

    public static MonthlyResponse from(MonthlyView v) {
        var r = v.report();
        var o = r.overhead();
        return new MonthlyResponse(r.month().toString(), r.produced(), r.sold(), r.soldRatio(), r.sales(),
                r.payments(), r.paidExpenses(), r.cashResult(), v.closed(), v.closedOn(),
                new OverheadResponse(o.value(), o.provisional(), o.reference()), r.profit(), r.profitInPacks(),
                r.cashResultInPacks(), r.reserves().stream()
                .map(s -> new ReserveResponse(s.name(), s.percentage(), s.amount())).toList());
    }
}
