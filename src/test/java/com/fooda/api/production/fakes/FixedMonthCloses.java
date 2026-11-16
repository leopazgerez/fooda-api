package com.fooda.api.production.fakes;

import com.fooda.api.core.application.port.out.MonthCloseRepository;
import com.fooda.api.core.domain.finance.MonthClose;

import java.time.YearMonth;
import java.util.*;

public class FixedMonthCloses implements MonthCloseRepository {

    public final Map<YearMonth, MonthClose> closes = new HashMap<>();

    @Override public Optional<MonthClose> find(YearMonth month) { return Optional.ofNullable(closes.get(month)); }
    @Override public List<MonthClose> findAll() { return List.copyOf(closes.values()); }
    @Override public MonthClose add(MonthClose c) { closes.put(c.month(), c); return c; }
    @Override public void softDelete(YearMonth month, long actorId) { closes.remove(month); }
}
