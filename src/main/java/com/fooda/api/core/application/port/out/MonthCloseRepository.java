package com.fooda.api.core.application.port.out;

import com.fooda.api.core.domain.finance.MonthClose;

import com.fooda.api.shared.domain.ConflictException;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public interface MonthCloseRepository {

    Optional<MonthClose> find(YearMonth month);

    List<MonthClose> findAll();

    MonthClose add(MonthClose close);

    void softDelete(YearMonth month, long actorId);

    /** 409 when the date's month is closed (§5.6). */
    default void requireOpen(LocalDate date) {
        if (find(YearMonth.from(date)).isPresent()) {
            throw new ConflictException("date", "Ese mes está cerrado");
        }
    }
}
