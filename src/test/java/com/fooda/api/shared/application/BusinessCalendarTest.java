package com.fooda.api.shared.application;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessCalendarTest {

    @Test
    void todayIsBuenosAiresDate() {
        var cal = new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-16T02:00:00Z"), ZoneOffset.UTC));
        assertThat(cal.today()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(cal.orToday(null)).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(cal.orToday(LocalDate.of(2026, 1, 2))).isEqualTo(LocalDate.of(2026, 1, 2));
    }
}
