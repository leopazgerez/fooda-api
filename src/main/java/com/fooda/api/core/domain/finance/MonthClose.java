package com.fooda.api.core.domain.finance;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

public record MonthClose(YearMonth month, BigDecimal overheadPerPack, LocalDate closedOn) {
}
