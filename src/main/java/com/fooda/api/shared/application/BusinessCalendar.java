package com.fooda.api.shared.application;

import com.fooda.api.shared.domain.ValidationException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** Business date: always Buenos Aires, whatever the clock's zone. */
public final class BusinessCalendar {

    public static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private final Clock clock;

    public BusinessCalendar(Clock clock) {
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }

    public LocalDate orToday(LocalDate date) {
        return date != null ? date : today();
    }

    /** {@link #orToday}, rejecting a future date with 400 on {@code date}. */
    public LocalDate pastOrToday(LocalDate date) {
        LocalDate day = orToday(date);
        if (day.isAfter(today())) {
            throw new ValidationException("date", "La fecha no puede ser futura");
        }
        return day;
    }
}
