package com.fooda.api.finance.infrastructure.out.persistence;

import com.fooda.api.TestcontainersConfiguration;
import com.fooda.api.config.JpaAuditingConfig;
import com.fooda.api.config.TimeConfig;
import com.fooda.api.core.domain.finance.AppliedOverhead;
import com.fooda.api.core.domain.finance.MonthClose;
import com.fooda.api.core.domain.finance.MonthlyReport;
import com.fooda.api.support.Actors;
import com.fooda.api.support.Db;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MonthClosePersistenceAdapter.class, JpaAuditingConfig.class, TimeConfig.class, TestcontainersConfiguration.class})
class MonthClosePersistenceAdapterTest {

    @Autowired MonthClosePersistenceAdapter closes;
    @Autowired JdbcTemplate jdbc;

    long actor;

    @BeforeEach
    void setUp() {
        Db.reset(jdbc);
        actor = Actors.insertUser(jdbc);
        Actors.actAs(actor);
    }

    @AfterEach
    void clear() {
        Actors.clear();
    }

    private static MonthClose close(YearMonth m) {
        MonthlyReport r = new MonthlyReport(m, 10, 4, new BigDecimal("0.3333333333"), new BigDecimal("100.50"),
                new BigDecimal("90"), new BigDecimal("20"), new BigDecimal("70"),
                new AppliedOverhead(new BigDecimal("12.3456"), false, true), new BigDecimal("1234.5678901"),
                new BigDecimal("3.5"), new BigDecimal("2.25"),
                List.of(new MonthlyReport.ReserveShare("Ahorro", new BigDecimal("10"), new BigDecimal("7.5"))));
        return new MonthClose(m, new BigDecimal("12.3456"), LocalDate.of(2026, 4, 2), r);
    }

    @Test
    void addThenFindRoundTripsTheReportWithFullPrecision() {
        YearMonth m = YearMonth.of(2026, 3);
        closes.add(close(m));
        MonthClose found = closes.find(m).orElseThrow();
        assertThat(found.month()).isEqualTo(m);
        assertThat(found.closedOn()).isEqualTo(LocalDate.of(2026, 4, 2));
        assertThat(found.overheadPerPack()).isEqualByComparingTo("12.3456");
        MonthlyReport r = found.report();
        assertThat(r.profit()).isEqualByComparingTo("1234.5678901");
        assertThat(r.soldRatio()).isEqualByComparingTo("0.3333333333");
        assertThat(r.overhead().reference()).isTrue();
        assertThat(r.produced()).isEqualTo(10);
        assertThat(r.reserves()).hasSize(1);
        assertThat(r.reserves().get(0).amount()).isEqualByComparingTo("7.5");
    }

    @Test
    void findAllListsOnlyLiveCloses() {
        closes.add(close(YearMonth.of(2026, 1)));
        closes.add(close(YearMonth.of(2026, 2)));
        closes.softDelete(YearMonth.of(2026, 1), actor);
        assertThat(closes.findAll()).extracting(MonthClose::month).containsExactly(YearMonth.of(2026, 2));
    }

    @Test
    void softDeleteReopensAndAllowsClosingAgain() {
        YearMonth m = YearMonth.of(2026, 3);
        closes.add(close(m));
        closes.softDelete(m, actor);
        assertThat(closes.find(m)).isEmpty();
        MonthClose again = new MonthClose(m, new BigDecimal("1"), LocalDate.of(2026, 4, 9), close(m).report());
        closes.add(again);
        assertThat(closes.find(m).orElseThrow().closedOn()).isEqualTo(LocalDate.of(2026, 4, 9));
    }

    @Test
    void duplicateLiveCloseViolatesMonthCloseMonthKey() {
        closes.add(close(YearMonth.of(2026, 3)));
        assertThatThrownBy(() -> closes.add(close(YearMonth.of(2026, 3))))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("month_close_month_key");
    }
}
