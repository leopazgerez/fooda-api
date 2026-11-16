package com.fooda.api.core.domain.sales;

import com.fooda.api.core.domain.catalog.Branch;
import com.fooda.api.core.domain.catalog.Shipping;
import com.fooda.api.core.domain.catalog.ShippingType;
import com.fooda.api.core.domain.expense.PaymentMethod;
import com.fooda.api.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SaleRulesTest {

    private static BigDecimal n(long v) {
        return BigDecimal.valueOf(v);
    }

    private static Sale sale(SaleType type, long discount, long shipping) {
        return new Sale(1L, LocalDate.parse("2026-11-10"), 1, null, type, null, PaymentMethod.TRANSFER, n(discount),
                n(shipping), null, List.of(new SaleLine(1, 1, 2, n(45000))));
    }

    private static Branch branch(boolean kitchen, boolean affectsPrice, Shipping shipping) {
        return new Branch(1L, "B", "", 0, 0, 0, shipping, affectsPrice, kitchen, 0L);
    }

    @Test
    void totalIsPacksTimesPriceMinusDiscountPlusShipping() {
        assertThat(SaleRules.total(sale(SaleType.RETAIL, 5000, 3000))).isEqualByComparingTo("88000");
    }

    @Test
    void internalUseAndPaidWithPacksAreNotIncome() {
        assertThat(SaleRules.total(sale(SaleType.INTERNAL_USE, 0, 0))).isEqualByComparingTo("0");
        assertThat(SaleRules.total(sale(SaleType.PAID_WITH_PACKS, 0, 0))).isEqualByComparingTo("0");
        assertThat(SaleRules.isIncome(SaleType.INTERNAL_USE)).isFalse();
        assertThat(SaleRules.isIncome(SaleType.WHOLESALE)).isTrue();
    }

    @Test
    void shippingPerPack() {
        Shipping amount = new Shipping(ShippingType.AMOUNT, n(9000));
        assertThat(SaleRules.shippingPerPack(branch(false, false, amount), n(45000))).isEqualByComparingTo("9000");
        assertThat(SaleRules.shippingPerPack(branch(true, false, amount), n(45000))).isEqualByComparingTo("0");
        assertThat(SaleRules.shippingPerPack(branch(false, true, amount), n(45000))).isEqualByComparingTo("0");
        Shipping pct = new Shipping(ShippingType.PERCENTAGE, n(10));
        assertThat(SaleRules.shippingPerPack(branch(false, false, pct), n(45000))).isEqualByComparingTo("4500");
    }

    @Test
    void lineProfitDiscountsCostProratedDiscountAndShipping() {
        Sale s = sale(SaleType.RETAIL, 4000, 0);
        assertThat(SaleRules.lineProfit(s.lines().get(0), s, n(24000), n(9000)))
                .isEqualByComparingTo(String.valueOf(2 * (45000 - 2000 - 24000 - 9000)));
        Sale u = sale(SaleType.INTERNAL_USE, 0, 0);
        assertThat(SaleRules.lineProfit(u.lines().get(0), u, n(24000), BigDecimal.ZERO)).isEqualByComparingTo("-48000");
    }

    @Test
    void saleLineRejectsNegativePrice() {
        assertThatThrownBy(() -> new SaleLine(1, 1, 1, n(-1)))
                .isInstanceOf(ValidationException.class).hasMessage("Revisá los precios");
    }

    @Test
    void saleTypeRejectsUnknownCode() {
        assertThat(SaleType.of("paid_with_packs")).isEqualTo(SaleType.PAID_WITH_PACKS);
        assertThatThrownBy(() -> SaleType.of("x")).isInstanceOf(ValidationException.class).hasMessage("Tipo de venta inválido");
    }
}
