package com.fooda.api.core.domain.finance;

import com.fooda.api.core.domain.catalog.*;
import com.fooda.api.core.domain.production.*;
import com.fooda.api.core.domain.sales.Sale;
import com.fooda.api.core.domain.sales.SaleLine;
import com.fooda.api.core.domain.sales.SaleType;
import com.fooda.api.core.domain.expense.PaymentMethod;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SaleProfitsTest {

    private static final LocalDate D = LocalDate.of(2026, 11, 3);
    private static final long PACK = 7L;

    private final Settings st = new Settings(new BigDecimal("45000"), new BigDecimal("20"), 4L, 1, 1,
            new BigDecimal("24000"), 3, 30, BigDecimal.TEN, List.of(), List.of(Settings.FIXED_EXPENSE_TYPE), null);
    private final Supply flour = new Supply(1L, "Harina", Unit.G, 1, null, BigDecimal.ONE, null, null, null, true, null);
    private final Branch jvg = new Branch(1L, "JVG", "", 10, 1, 1,
            new Shipping(ShippingType.AMOUNT, new BigDecimal("9000")), false, false, null);
    private final Production lot = lot(10L);

    private Production lot(long id) {
        Dish dish = new Dish(1L, "Plato", List.of(new RecipeLine(1L, BigDecimal.ONE, Unit.G, BigDecimal.ONE)), null);
        ProductionSnapshot snap = new ProductionSnapshot(Map.of(1L, new BigDecimal("10000")), Map.of(1L, dish),
                List.of(new SnapshotPack(PACK, 1L, List.of(new MenuSlot(1, 1L)))), st, List.of(flour));
        return new Production(id, "1 NOV 26", 1, ProductionStatus.CLOSED, D, D, List.of(), snap,
                Map.of(1L, new BigDecimal("14000")), null, null);
    }

    private Sale sale(long productionId, long branchId, int qty) {
        return new Sale(1L, D, branchId, null, SaleType.RETAIL, null, PaymentMethod.CASH, null, null, null,
                List.of(new SaleLine(productionId, PACK, qty, new BigDecimal("45000"))));
    }

    private SaleProfits profits(Map<YearMonth, MonthClose> closes, Branch... branches) {
        return new SaleProfits(List.of(lot), List.of(), List.of(), m -> Optional.ofNullable(closes.get(m)), st, List.of(branches));
    }

    @Test
    void profitUsesActualCostPlusOverheadOfTheLotMonthAndBranchShipping() {
        var closes = Map.of(YearMonth.of(2026, 11), new MonthClose(YearMonth.of(2026, 11), new BigDecimal("800"), D));
        // 2 * (45000 - (14000 actual + 800 overhead) - 9000 shipping)
        assertThat(profits(closes, jvg).of(sale(10L, 1L, 2))).isEqualByComparingTo("42400");
    }

    @Test
    void missingLotCostsZeroAndMissingBranchShipsZero() {
        assertThat(profits(Map.of()).of(sale(99L, 5L, 2))).isEqualByComparingTo("90000");
    }

    @Test
    void overheadIsComputedOncePerMonth() {
        var closes = Map.of(YearMonth.of(2026, 11), new MonthClose(YearMonth.of(2026, 11), new BigDecimal("800"), D));
        SaleProfits p = profits(closes, jvg);
        BigDecimal one = p.of(sale(10L, 1L, 1));
        assertThat(p.of(sale(10L, 1L, 1))).isEqualByComparingTo(one);
        assertThat(p.of(sale(10L, 1L, 2))).isEqualByComparingTo(one.multiply(BigDecimal.valueOf(2)));
    }
}
