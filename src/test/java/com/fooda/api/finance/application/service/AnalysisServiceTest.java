package com.fooda.api.finance.application.service;

import com.fooda.api.catalog.fakes.InMemoryBranchRepository;
import com.fooda.api.catalog.fakes.InMemoryDishRepository;
import com.fooda.api.catalog.fakes.InMemoryPackRepository;
import com.fooda.api.core.domain.catalog.*;
import com.fooda.api.core.domain.sales.Sale;
import com.fooda.api.core.domain.sales.SaleLine;
import com.fooda.api.core.domain.sales.SaleType;
import com.fooda.api.finance.application.port.in.VersionAt;
import com.fooda.api.sales.fakes.InMemorySaleRepository;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.shared.application.exception.ResourceNotFoundException;
import com.fooda.api.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisServiceTest {

    private final InMemorySaleRepository sales = new InMemorySaleRepository();
    private final InMemoryPackRepository packs = new InMemoryPackRepository();
    private final InMemoryDishRepository dishes = new InMemoryDishRepository();
    private final InMemoryBranchRepository branches = new InMemoryBranchRepository();
    private final AnalysisService service = new AnalysisService(sales, packs, dishes, branches,
            new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-16T15:00:00Z"), ZoneOffset.UTC)));

    private final long pack = packs.save(new Pack(null, 1, "Clásico", true, null, null)).id();
    private final long dishA = dish("Milanesa");
    private final long dishB = dish("Tarta");

    private long dish(String name) {
        return dishes.save(new Dish(null, name, List.of(new RecipeLine(1L, BigDecimal.ONE, Unit.G, BigDecimal.ONE)),
                null)).id();
    }

    private void version(String from, long first, long fifth) {
        packs.addVersion(new PackVersion(null, pack, LocalDate.parse(from), List.of(new MenuSlot(1, first),
                new MenuSlot(2, dishA), new MenuSlot(3, dishA), new MenuSlot(4, dishA), new MenuSlot(5, fifth))));
    }

    @Test
    void defaultsAreTwoMonthsBackUntilToday() {
        var r = service.packs(null, null, null);
        assertThat(r.from()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(r.to()).isEqualTo(LocalDate.of(2026, 11, 16));
        assertThat(r.groupBy()).isEqualTo("month");
    }

    @Test
    void changesSkipTheFirstVersionOfEachPack() {
        version("2026-09-05", dishA, dishA);
        version("2026-10-05", dishB, dishA);
        version("2026-08-01", dishA, dishA);
        sales.add(new Sale(null, LocalDate.of(2026, 10, 7), 3, null, SaleType.RETAIL, null, null, null, null, null,
                List.of(new SaleLine(1L, pack, 2, new BigDecimal("10")))));
        var r = service.packs(null, null, "month");
        assertThat(r.changes()).extracting(c -> c.validFrom())
                .containsExactly(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 10, 5));
        assertThat(r.rows()).singleElement().satisfies(row -> {
            assertThat(row.pack()).isEqualTo("Clásico");
            assertThat(row.amount()).isEqualByComparingTo("20");
        });
    }

    @Test
    void versionAtReturnsTheOneInForceAndItsDiff() {
        version("2026-09-01", dishA, dishA);
        version("2026-10-01", dishB, dishA);
        var first = service.versionAt(pack, LocalDate.of(2026, 9, 15));
        assertThat(first.until()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(first.previous()).isNull();
        assertThat(first.changes()).isEmpty();
        var second = service.versionAt(pack, LocalDate.of(2026, 10, 1));
        assertThat(second.until()).isNull();
        assertThat(second.previous().validFrom()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(second.changes()).containsExactly(new VersionAt.Change(1, "Milanesa", "Tarta", 1));
        assertThat(second.menus().get(0)).isEqualTo(new VersionAt.Menu(1, "Tarta"));
        assertThat(service.versionAt(pack, null).version().validFrom()).isEqualTo(LocalDate.of(2026, 10, 1));
    }

    @Test
    void versionAtErrors() {
        version("2026-09-01", dishA, dishA);
        assertThatThrownBy(() -> service.versionAt(999, null)).isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Pack no encontrado");
        assertThatThrownBy(() -> service.versionAt(pack, LocalDate.of(2026, 8, 31)))
                .isInstanceOf(ValidationException.class).hasMessage("El pack no tenía versión en esa fecha");
    }

    @Test
    void unknownPackAndBranchShowADash() {
        sales.add(new Sale(null, LocalDate.of(2026, 10, 7), 77, null, SaleType.RETAIL, null, null, null, null, null,
                List.of(new SaleLine(1L, 888, 1, BigDecimal.TEN))));
        assertThat(service.packs(null, null, null).rows()).singleElement().satisfies(r -> {
            assertThat(r.pack()).isEqualTo("—");
            assertThat(r.branch()).isEqualTo("—");
        });
    }
}
