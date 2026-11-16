package com.fooda.api.core.domain.finance;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record MonthlyReport(YearMonth month, int produced, int sold, BigDecimal soldRatio, BigDecimal sales,
                            BigDecimal payments, BigDecimal paidExpenses, BigDecimal cashResult,
                            AppliedOverhead overhead, BigDecimal profit, BigDecimal profitInPacks,
                            BigDecimal cashResultInPacks, List<ReserveShare> reserves) {

    public MonthlyReport {
        reserves = List.copyOf(reserves);
    }

    public record ReserveShare(String name, BigDecimal percentage, BigDecimal amount) {
    }
}
