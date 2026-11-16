package com.fooda.api.core.domain.finance;

import com.fooda.api.core.domain.catalog.Branch;
import com.fooda.api.core.domain.catalog.Settings;
import com.fooda.api.core.domain.catalog.Supply;
import com.fooda.api.core.domain.expense.Expense;
import com.fooda.api.core.domain.production.Production;
import com.fooda.api.core.domain.production.ProductionRules;
import com.fooda.api.core.domain.sales.Sale;
import com.fooda.api.core.domain.sales.SaleLine;
import com.fooda.api.core.domain.sales.SaleRules;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Profit of a sale: actual pack cost plus the overhead of the lot's month, minus branch shipping. */
public final class SaleProfits {

    private final List<Production> productions;
    private final List<Supply> live;
    private final List<Expense> expenses;
    private final Function<YearMonth, Optional<MonthClose>> closes;
    private final Settings settings;
    private final Map<Long, Production> lots;
    private final Map<Long, Branch> branches;
    private final Map<YearMonth, BigDecimal> overheads = new HashMap<>();

    public SaleProfits(List<Production> productions, List<Supply> liveSupplies, List<Expense> expenses,
                       Function<YearMonth, Optional<MonthClose>> closes, Settings settings, List<Branch> branches) {
        this.productions = productions;
        this.live = liveSupplies;
        this.expenses = expenses;
        this.closes = closes;
        this.settings = settings;
        this.lots = productions.stream().collect(Collectors.toMap(Production::id, Function.identity()));
        this.branches = branches.stream().collect(Collectors.toMap(Branch::id, Function.identity()));
    }

    public BigDecimal of(Sale sale) {
        Branch branch = branches.get(sale.branchId());
        BigDecimal shipping = branch == null ? BigDecimal.ZERO
                : SaleRules.shippingPerPack(branch, settings.packPrice());
        return sale.lines().stream().map(l -> SaleRules.lineProfit(l, sale, actualCost(l), shipping))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal actualCost(SaleLine l) {
        Production p = lots.get(l.productionId());
        if (p == null) {
            return BigDecimal.ZERO;
        }
        YearMonth month = YearMonth.from(p.closedOn() != null ? p.closedOn() : p.start());
        BigDecimal overhead = overheads.computeIfAbsent(month, m -> Finance.applicable(m,
                closes.apply(m), expenses, productions, settings).value());
        return ProductionRules.packCost(p, l.packId(), live).actual().add(overhead);
    }
}
