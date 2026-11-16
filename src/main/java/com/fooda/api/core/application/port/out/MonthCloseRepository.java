package com.fooda.api.core.application.port.out;

import com.fooda.api.core.domain.finance.MonthClose;

import java.time.YearMonth;
import java.util.Optional;

public interface MonthCloseRepository {

    Optional<MonthClose> find(YearMonth month);
}
