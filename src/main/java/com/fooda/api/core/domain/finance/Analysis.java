package com.fooda.api.core.domain.finance;

import com.fooda.api.core.domain.catalog.MenuSlot;
import com.fooda.api.core.domain.catalog.PackVersion;
import com.fooda.api.core.domain.sales.Sale;
import com.fooda.api.core.domain.sales.SaleLine;
import com.fooda.api.core.domain.sales.SaleRules;
import com.fooda.api.shared.domain.ValidationException;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Port de /analisis/packs (fooda-admin rutas-reportes.ts). */
public final class Analysis {

    public enum GroupBy {
        MONTH("month"), WEEK("week");

        private final String code;

        GroupBy(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        public static GroupBy of(String code) {
            if (code == null) {
                return MONTH;
            }
            for (GroupBy g : values()) {
                if (g.code.equals(code)) {
                    return g;
                }
            }
            throw new ValidationException("groupBy", "Agrupá por month o week");
        }
    }

    public record Row(String period, long packId, long branchId, int quantity, BigDecimal amount) {
    }

    public record MenuChange(int number, long beforeDishId, long afterDishId, int newNumber) {
    }

    private Analysis() {
    }

    public static String period(LocalDate d, GroupBy g) {
        return g == GroupBy.MONTH ? YearMonth.from(d).toString()
                : d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
    }

    public static List<Row> rows(List<Sale> sales, LocalDate from, LocalDate to, GroupBy g) {
        record Key(String period, long packId, long branchId) {
        }
        record Acc(int quantity, BigDecimal amount) {
        }
        Map<Key, Acc> acc = new TreeMap<>(Comparator.comparing(Key::period).thenComparingLong(Key::packId)
                .thenComparingLong(Key::branchId));
        for (Sale s : sales) {
            if (!SaleRules.isIncome(s.type()) || s.date().isBefore(from) || s.date().isAfter(to)) {
                continue;
            }
            for (SaleLine l : s.lines()) {
                acc.merge(new Key(period(s.date(), g), l.packId(), s.branchId()),
                        new Acc(l.quantity(), l.unitPrice().multiply(BigDecimal.valueOf(l.quantity()))),
                        (x, y) -> new Acc(x.quantity() + y.quantity(), x.amount().add(y.amount())));
            }
        }
        List<Row> out = new ArrayList<>();
        acc.forEach((k, v) -> out.add(new Row(k.period(), k.packId(), k.branchId(), v.quantity(), v.amount())));
        return out;
    }

    /** Compares menus by position; {@code number} is the previous version's. */
    public static List<MenuChange> menuChanges(PackVersion previous, PackVersion version) {
        List<MenuChange> out = new ArrayList<>();
        for (int k = 0; k < Math.min(previous.menus().size(), version.menus().size()); k++) {
            MenuSlot a = previous.menus().get(k);
            MenuSlot b = version.menus().get(k);
            if (a.dishId() != b.dishId() || a.number() != b.number()) {
                out.add(new MenuChange(a.number(), a.dishId(), b.dishId(), b.number()));
            }
        }
        return out;
    }
}
