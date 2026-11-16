package com.fooda.api.finance.application.port.in;

import java.time.YearMonth;
import java.util.List;

public interface FinanceUseCase {

    /** Months with data plus the current one, newest first, at most 12. */
    List<MonthlyView> monthly();

    MonthlyView month(YearMonth month);

    /** Freezes the month's overhead and report; {@code month} is "YYYY-MM". */
    MonthlyView close(String month);

    void reopen(String month, long actorId);
}
