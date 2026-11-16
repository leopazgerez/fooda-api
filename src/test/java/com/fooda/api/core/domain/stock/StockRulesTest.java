package com.fooda.api.core.domain.stock;

import com.fooda.api.core.domain.catalog.Branch;
import com.fooda.api.core.domain.catalog.Shipping;
import com.fooda.api.core.domain.catalog.ShippingType;
import com.fooda.api.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import com.fooda.api.core.domain.production.Production;
import com.fooda.api.core.domain.production.ProductionStatus;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockRulesTest {

    private static final LocalDate NOV_1 = LocalDate.of(2026, 11, 1);

    private static StockMovement m(long branch, long production, long pack, int qty, MovementType type) {
        return new StockMovement(null, NOV_1, branch, production, pack, qty, type, null, null);
    }

    private final List<StockMovement> movs = List.of(
            m(1, 2, 1, 10, MovementType.PRODUCTION), m(1, 1, 1, 10, MovementType.PRODUCTION),
            m(1, 1, 1, -4, MovementType.TRANSFER_OUT), m(2, 1, 1, 4, MovementType.TRANSFER_IN),
            m(2, 1, 1, -1, MovementType.SALE), m(1, 1, 2, 5, MovementType.PRODUCTION),
            m(1, 1, 2, -5, MovementType.SALE));

    private final Branch branch = new Branch(1L, "Centro", "", 60, 8, 20,
            new Shipping(ShippingType.values()[0], BigDecimal.ZERO), false, false, null);

    private static StockMovement on(String date, int qty) {
        return new StockMovement(null, LocalDate.parse(date), 1, 1, 1, qty, MovementType.TRANSFER_IN, null, null);
    }

    @Test
    void availableOnADayCountsOnlyWhatHadArrivedAndKeepsLaterExitsCovered() {
        List<StockMovement> lot = List.of(on("2026-11-10", 10), on("2026-11-12", -8));
        assertThat(StockRules.available(lot, 1, 1, 1, LocalDate.parse("2026-11-01"))).isZero();
        assertThat(StockRules.available(lot, 1, 1, 1, LocalDate.parse("2026-11-10"))).isEqualTo(2);
        assertThat(StockRules.available(lot, 1, 1, 1, LocalDate.parse("2026-11-15"))).isEqualTo(2);
        assertThat(StockRules.firstShortage(lot, 1, List.of(new OutLine(1, 1, 1)), LocalDate.parse("2026-11-05")))
                .contains(new Shortage(1, 1, 0, 1));
    }

    @Test
    void exitBeforeALotClosedIs400ButTheInitialLotIsExempt() {
        assertThatThrownBy(() -> StockRules.requireAfterLotsClosed(List.of(lot(1, "2026-11-10")),
                LocalDate.parse("2026-11-09"))).isInstanceOf(ValidationException.class)
                .hasMessage("La fecha no puede ser anterior al cierre del lote");
        StockRules.requireAfterLotsClosed(List.of(lot(0, "2026-11-10")), LocalDate.parse("2026-11-09"));
        StockRules.requireAfterLotsClosed(List.of(lot(1, "2026-11-10")), LocalDate.parse("2026-11-10"));
    }

    private static Production lot(int number, String closedOn) {
        LocalDate d = LocalDate.parse(closedOn);
        return new Production(1L, "x", number, ProductionStatus.CLOSED, d, d, List.of(), null, null, null, null);
    }

    @Test
    void currentSumsByBranchProductionPackOmitsZerosAndSortsFifo() {
        assertThat(StockRules.current(movs)).containsExactly(
                new StockRow(1, 1, 1, 6), new StockRow(2, 1, 1, 3), new StockRow(1, 2, 1, 10));
    }

    @Test
    void availableByBranchProductionPack() {
        assertThat(StockRules.available(movs, 2, 1, 1, NOV_1)).isEqualTo(3);
        assertThat(StockRules.available(movs, 1, 1, 2, NOV_1)).isZero();
    }

    @Test
    void firstShortageGroupsRepeatedLines() {
        assertThat(StockRules.firstShortage(movs, 2, List.of(new OutLine(1, 1, 2), new OutLine(1, 1, 2)), NOV_1))
                .contains(new Shortage(1, 1, 3, 4));
        assertThat(StockRules.firstShortage(movs, 2, List.of(new OutLine(1, 1, 3)), NOV_1)).isEmpty();
    }

    @Test
    void outLineRejectsNonPositiveAndHugeQuantities() {
        assertThatThrownBy(() -> new OutLine(1, 1, 0)).isInstanceOf(ValidationException.class)
                .hasMessage("Las cantidades deben ser enteros mayores a cero");
        assertThatThrownBy(() -> new OutLine(1, 1, StockRules.MAX_QUANTITY + 1)).isInstanceOf(ValidationException.class)
                .hasMessage("La cantidad es demasiado grande");
    }

    @Test
    void alertsLowStockAndFreeSlots() {
        assertThat(StockRules.alerts(branch, 5)).containsExactly(BranchAlert.LOW_STOCK, BranchAlert.FREE_SLOTS);
        assertThat(StockRules.alerts(branch, 30)).containsExactly(BranchAlert.FREE_SLOTS);
        assertThat(StockRules.alerts(branch, 50)).isEmpty();
    }

    @Test
    void largestLinesStillAddUp() {
        var out = StockRules.firstShortage(movs, 1, List.of(new OutLine(1, 1, StockRules.MAX_QUANTITY),
                new OutLine(1, 1, StockRules.MAX_QUANTITY)), NOV_1);
        assertThat(out).contains(new Shortage(1, 1, 6, 2 * StockRules.MAX_QUANTITY));
    }
}
