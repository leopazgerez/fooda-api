package com.fooda.api.core.domain.finance;

import com.fooda.api.core.domain.catalog.MenuSlot;
import com.fooda.api.core.domain.catalog.PackVersion;
import com.fooda.api.core.domain.sales.Sale;
import com.fooda.api.core.domain.sales.SaleLine;
import com.fooda.api.core.domain.sales.SaleType;
import com.fooda.api.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.fooda.api.core.domain.finance.Analysis.GroupBy.MONTH;
import static com.fooda.api.core.domain.finance.Analysis.GroupBy.WEEK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnalysisTest {

    private static Sale sale(String date, long branch, SaleType type, SaleLine... lines) {
        return new Sale(null, LocalDate.parse(date), branch, null, type, null, null, null, null, null,
                List.of(lines));
    }

    private static SaleLine line(long pack, int qty, String price) {
        return new SaleLine(1L, pack, qty, new BigDecimal(price));
    }

    @Test
    void weekPeriodIsTheMondayEvenAcrossMonths() {
        assertThat(Analysis.period(LocalDate.of(2026, 11, 1), WEEK)).isEqualTo("2026-10-26");
        assertThat(Analysis.period(LocalDate.of(2026, 11, 2), WEEK)).isEqualTo("2026-11-02");
        assertThat(Analysis.period(LocalDate.of(2026, 11, 20), MONTH)).isEqualTo("2026-11");
    }

    @Test
    void rowsGroupIncomeSalesByPeriodPackAndBranch() {
        var sales = List.of(
                sale("2026-11-03", 2, SaleType.RETAIL, line(5, 2, "100")),
                sale("2026-11-20", 2, SaleType.WHOLESALE, line(5, 3, "90")),
                sale("2026-11-04", 2, SaleType.INTERNAL_USE, line(5, 9, "100")),
                sale("2026-12-30", 2, SaleType.RETAIL, line(5, 9, "100")),
                sale("2026-10-31", 1, SaleType.RETAIL, line(6, 1, "50")));
        var rows = Analysis.rows(sales, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 30), MONTH);
        assertThat(rows).containsExactly(
                new Analysis.Row("2026-10", 6, 1, 1, new BigDecimal("50")),
                new Analysis.Row("2026-11", 5, 2, 5, new BigDecimal("470")));
    }

    @Test
    void menuChangesComparesByPosition() {
        var prev = new PackVersion(1L, 1, LocalDate.of(2026, 1, 1), List.of(new MenuSlot(11, 100), new MenuSlot(12, 101),
                new MenuSlot(13, 102), new MenuSlot(14, 103), new MenuSlot(15, 104)));
        var next = new PackVersion(2L, 1, LocalDate.of(2026, 2, 1), List.of(new MenuSlot(18, 200), new MenuSlot(12, 101),
                new MenuSlot(13, 102), new MenuSlot(14, 103), new MenuSlot(15, 104)));
        assertThat(Analysis.menuChanges(prev, next)).containsExactly(new Analysis.MenuChange(11, 100, 200, 18));
    }

    @Test
    void groupByRejectsUnknownCode() {
        assertThat(Analysis.GroupBy.of(null)).isEqualTo(MONTH);
        assertThat(Analysis.GroupBy.of("week")).isEqualTo(WEEK);
        assertThatThrownBy(() -> Analysis.GroupBy.of("year")).isInstanceOf(ValidationException.class)
                .hasMessage("Agrupá por month o week");
    }
}
