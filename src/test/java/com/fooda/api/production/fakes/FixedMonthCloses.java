package com.fooda.api.production.fakes;

import com.fooda.api.core.application.port.out.MonthCloseRepository;
import com.fooda.api.core.domain.finance.MonthClose;

import java.time.YearMonth;
import java.util.*;

public class FixedMonthCloses implements MonthCloseRepository {

    public final Map<YearMonth, MonthClose> closes = new HashMap<>();

    @Override public Optional<MonthClose> find(YearMonth month) { return Optional.ofNullable(closes.get(month)); }
}
