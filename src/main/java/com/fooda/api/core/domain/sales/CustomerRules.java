package com.fooda.api.core.domain.sales;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class CustomerRules {

    private CustomerRules() {
    }

    private static List<Sale> purchases(long customerId, List<Sale> sales) {
        return sales.stream().filter(s -> s.customerId() != null && s.customerId() == customerId && SaleRules.isIncome(s.type()))
                .toList();
    }

    /** At least one income purchase in each of the {@code months} full calendar months before today's month. */
    public static boolean isRegular(long customerId, List<Sale> sales, LocalDate today, int months) {
        Set<YearMonth> bought = purchases(customerId, sales).stream().map(s -> YearMonth.from(s.date()))
                .collect(Collectors.toSet());
        YearMonth current = YearMonth.from(today);
        for (int k = 1; k <= months; k++) {
            if (!bought.contains(current.minusMonths(k))) {
                return false;
            }
        }
        return true;
    }

    public static List<FollowUp> followUps(List<Customer> customers, List<Sale> sales, LocalDate today, int days) {
        List<FollowUp> out = new ArrayList<>();
        for (Customer c : customers) {
            Optional<Sale> last = purchases(c.id(), sales).stream().max(Comparator.comparing(Sale::date));
            if (last.isEmpty()) {
                continue;
            }
            long since = ChronoUnit.DAYS.between(last.get().date(), today);
            if (since >= days) {
                List<Long> packIds = last.get().lines().stream().map(SaleLine::packId).distinct().toList();
                out.add(new FollowUp(c, last.get().date(), since, packIds));
            }
        }
        out.sort(Comparator.comparingLong(FollowUp::daysWithoutPurchase).reversed());
        return List.copyOf(out);
    }

    /** Customers whose birthday is today; those born on Feb 29 are listed on Feb 28 in non-leap years. */
    public static List<Customer> birthdaysToday(List<Customer> customers, LocalDate today) {
        String md = today.toString().substring(5);
        boolean leapDayMissing = !today.isLeapYear() && "02-28".equals(md);
        return customers.stream()
                .filter(c -> md.equals(c.birthday()) || leapDayMissing && "02-29".equals(c.birthday()))
                .toList();
    }
}
