package com.fooda.api.finance.infrastructure.out.persistence;

import com.fooda.api.core.application.port.out.MonthCloseRepository;
import com.fooda.api.core.domain.finance.MonthClose;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.Optional;

/** Provisional {@link MonthCloseRepository}: no month is ever closed. Block 5 (finance) replaces it with the real adapter. */
@Component
public class NoMonthCloses implements MonthCloseRepository {

    @Override
    public Optional<MonthClose> find(YearMonth month) {
        return Optional.empty();
    }
}
