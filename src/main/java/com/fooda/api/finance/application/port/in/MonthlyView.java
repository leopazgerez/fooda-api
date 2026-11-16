package com.fooda.api.finance.application.port.in;

import com.fooda.api.core.domain.finance.MonthlyReport;

import java.time.LocalDate;

/** A month's report; {@code closedOn} is null while the month is open. */
public record MonthlyView(MonthlyReport report, LocalDate closedOn) {

    public boolean closed() {
        return closedOn != null;
    }
}
