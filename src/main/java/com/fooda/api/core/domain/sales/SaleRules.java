package com.fooda.api.core.domain.sales;

import com.fooda.api.core.domain.catalog.Branch;
import com.fooda.api.core.domain.catalog.ShippingType;
import com.fooda.api.shared.domain.Decimals;

import java.math.BigDecimal;
import java.util.List;

public final class SaleRules {

    private SaleRules() {
    }

    public static boolean isIncome(SaleType type) {
        return type != SaleType.INTERNAL_USE && type != SaleType.PAID_WITH_PACKS;
    }

    public static int packs(List<SaleLine> lines) {
        return lines.stream().mapToInt(SaleLine::quantity).sum();
    }

    public static BigDecimal total(Sale sale) {
        if (!isIncome(sale.type())) {
            return BigDecimal.ZERO;
        }
        BigDecimal sum = sale.lines().stream().map(l -> l.unitPrice().multiply(BigDecimal.valueOf(l.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.subtract(sale.discount()).add(sale.shipping());
    }

    public static BigDecimal shippingPerPack(Branch branch, BigDecimal packPrice) {
        if (branch.kitchen() || branch.affectsPrice()) {
            return BigDecimal.ZERO;
        }
        var s = branch.shipping();
        return s.type() == ShippingType.AMOUNT ? s.value()
                : s.value().divide(BigDecimal.valueOf(100), Decimals.MC).multiply(packPrice);
    }

    public static BigDecimal lineProfit(SaleLine line, Sale sale, BigDecimal actualCostPerPack, BigDecimal shipping) {
        BigDecimal qty = BigDecimal.valueOf(line.quantity());
        if (!isIncome(sale.type())) {
            return actualCostPerPack.multiply(qty).negate();
        }
        int packs = packs(sale.lines());
        BigDecimal discount = packs == 0 ? BigDecimal.ZERO : sale.discount().divide(BigDecimal.valueOf(packs), Decimals.MC);
        return line.unitPrice().subtract(discount).subtract(actualCostPerPack).subtract(shipping).multiply(qty);
    }
}
