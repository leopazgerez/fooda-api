package com.fooda.api.core.domain.sales;

import com.fooda.api.core.domain.expense.PaymentMethod;
import com.fooda.api.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerRulesTest {

    private static Sale sale(long customerId, String date, SaleType type) {
        return new Sale(1L, LocalDate.parse(date), 1, customerId, type, null, PaymentMethod.TRANSFER, null, null, null,
                List.of(new SaleLine(1, 1, 2, BigDecimal.valueOf(45000))));
    }

    private static Customer cust(long id, String birthday) {
        return new Customer(id, "C" + id, "X", null, null, birthday, null, 0L);
    }

    private static final LocalDate TODAY = LocalDate.parse("2026-11-15");

    @Test
    void regularNeedsPurchaseInEachPreviousFullMonth() {
        List<Sale> vs = List.of(sale(1, "2026-09-05", SaleType.RETAIL), sale(1, "2026-10-20", SaleType.RETAIL),
                sale(2, "2026-09-05", SaleType.RETAIL), sale(2, "2026-11-02", SaleType.RETAIL));
        assertThat(CustomerRules.isRegular(1, vs, TODAY, 2)).isTrue();
        assertThat(CustomerRules.isRegular(2, vs, TODAY, 2)).isFalse();
        assertThat(CustomerRules.isRegular(3, List.of(sale(3, "2026-09-05", SaleType.RETAIL),
                sale(3, "2026-10-05", SaleType.INTERNAL_USE)), TODAY, 2)).isFalse();
    }

    @Test
    void followUpsOldestFirstAndNeverBoughtExcluded() {
        List<Sale> vs = List.of(sale(1, "2026-10-16", SaleType.RETAIL), sale(2, "2026-10-17", SaleType.RETAIL),
                sale(3, "2026-09-01", SaleType.RETAIL), sale(3, "2026-10-01", SaleType.RETAIL));
        List<FollowUp> r = CustomerRules.followUps(List.of(cust(1, null), cust(2, null), cust(3, null), cust(4, null)), vs, TODAY, 30);
        assertThat(r).extracting(f -> f.customer().id(), FollowUp::daysWithoutPurchase, FollowUp::lastPurchase)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(3L, 45L, LocalDate.parse("2026-10-01")),
                        org.assertj.core.groups.Tuple.tuple(1L, 30L, LocalDate.parse("2026-10-16")));
        assertThat(r.get(0).packIds()).containsExactly(1L);
    }

    @Test
    void leapDayBirthdaysShowOnFeb28InNonLeapYears() {
        List<Customer> cs = List.of(cust(1, "02-29"), cust(2, "02-28"));
        assertThat(CustomerRules.birthdaysToday(cs, LocalDate.parse("2027-02-28"))).extracting(Customer::id)
                .containsExactly(1L, 2L);
        assertThat(CustomerRules.birthdaysToday(cs, LocalDate.parse("2028-02-28"))).extracting(Customer::id)
                .containsExactly(2L);
        assertThat(CustomerRules.birthdaysToday(cs, LocalDate.parse("2028-02-29"))).extracting(Customer::id)
                .containsExactly(1L);
    }

    @Test
    void birthdaysToday() {
        assertThat(CustomerRules.birthdaysToday(List.of(cust(1, "11-15"), cust(2, "11-16"), cust(3, null)), TODAY))
                .extracting(Customer::id).containsExactly(1L);
    }

    @Test
    void customerBirthdayNormalizesAndRejectsImpossibleDates() {
        assertThat(cust(1, "2026-02-29").birthday()).isEqualTo("02-29");
        assertThat(cust(1, "").birthday()).isNull();
        assertThat(cust(1, null).birthday()).isNull();
        for (String bad : List.of("02-30", "13-01")) {
            assertThatThrownBy(() -> cust(1, bad)).isInstanceOf(ValidationException.class)
                    .hasMessage("Cumpleaños inválido (MM-DD)");
        }
    }

    @Test
    void customerRequiresNames() {
        assertThatThrownBy(() -> new Customer(null, " ", "X", null, null, null, null, null)).hasMessage("Completá nombre");
        assertThatThrownBy(() -> new Customer(null, "A", " ", null, null, null, null, null)).hasMessage("Completá apellido");
        assertThat(cust(1, null).fullName()).isEqualTo("C1 X");
    }
}
