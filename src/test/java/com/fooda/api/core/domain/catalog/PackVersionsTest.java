package com.fooda.api.core.domain.catalog;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class PackVersionsTest {

    private static PackVersion v(long id, long packId, String from) {
        return new PackVersion(id, packId, LocalDate.parse(from),
                IntStream.rangeClosed(1, 5).mapToObj(n -> new MenuSlot(n, n)).toList());
    }

    private final List<PackVersion> versions = List.of(
            v(1, 7, "2026-10-01"), v(3, 7, "2026-11-15"), v(2, 7, "2026-11-15"), v(9, 8, "2026-10-15"));

    @Test
    void currentIsLastByValidityUpToDay() {
        assertThat(PackVersions.current(versions, 7, LocalDate.parse("2026-11-01"))).map(PackVersion::id).hasValue(1L);
    }

    @Test
    void sameValidFromHighestIdWins() {
        assertThat(PackVersions.current(versions, 7, LocalDate.parse("2026-11-20"))).map(PackVersion::id).hasValue(3L);
    }

    @Test
    void beforeFirstVersionIsEmpty() {
        assertThat(PackVersions.current(versions, 7, LocalDate.parse("2026-09-30"))).isEmpty();
    }
}
