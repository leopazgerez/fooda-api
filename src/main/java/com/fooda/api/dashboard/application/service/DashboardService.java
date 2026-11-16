package com.fooda.api.dashboard.application.service;

import com.fooda.api.core.application.port.out.BranchRepository;
import com.fooda.api.core.application.port.out.PackRepository;
import com.fooda.api.core.application.port.out.SaleRepository;
import com.fooda.api.core.domain.catalog.Branch;
import com.fooda.api.core.domain.catalog.PackagingType;
import com.fooda.api.core.domain.finance.Analysis;
import com.fooda.api.core.domain.production.ProductionStatus;
import com.fooda.api.core.domain.sales.Sale;
import com.fooda.api.core.domain.sales.SaleRules;
import com.fooda.api.dashboard.application.port.in.DashboardUseCase;
import com.fooda.api.dashboard.application.port.in.DashboardView;
import com.fooda.api.dashboard.application.port.in.DashboardView.*;
import com.fooda.api.finance.application.port.in.FinanceUseCase;
import com.fooda.api.production.application.port.in.PackagingStatus;
import com.fooda.api.production.application.port.in.ProductionSummary;
import com.fooda.api.production.application.port.in.ProductionUseCase;
import com.fooda.api.production.application.port.in.PurchaseUseCase;
import com.fooda.api.sales.application.port.in.CustomerUseCase;
import com.fooda.api.sales.application.port.in.FollowUpsView;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.stock.application.port.in.StockUseCase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DashboardService implements DashboardUseCase {

    private static final int LAST_PRODUCTIONS = 5;

    private final FinanceUseCase finance;
    private final StockUseCase stock;
    private final ProductionUseCase productions;
    private final PurchaseUseCase purchases;
    private final CustomerUseCase customers;
    private final SaleRepository sales;
    private final PackRepository packs;
    private final BranchRepository branches;
    private final BusinessCalendar calendar;

    public DashboardService(FinanceUseCase finance, StockUseCase stock, ProductionUseCase productions,
                            PurchaseUseCase purchases, CustomerUseCase customers, SaleRepository sales,
                            PackRepository packs, BranchRepository branches, BusinessCalendar calendar) {
        this.finance = finance;
        this.stock = stock;
        this.productions = productions;
        this.purchases = purchases;
        this.customers = customers;
        this.sales = sales;
        this.packs = packs;
        this.branches = branches;
        this.calendar = calendar;
    }

    @Override
    public DashboardView get() {
        LocalDate today = calendar.today();
        YearMonth month = YearMonth.from(today);
        List<Sale> allSales = sales.findAll();
        List<ProductionSummary> summaries = productions.summaries();
        List<ProductionSummary> withLines = summaries.stream().filter(p -> !p.packLines().isEmpty()).toList();
        return new DashboardView(today, finance.month(month), previousPeriod(allSales, today, month),
                stock.view(null, null, null).byBranch(), salesByPack(allSales, month),
                withLines.stream().sorted(Comparator.comparingLong(ProductionSummary::id).reversed())
                        .limit(LAST_PRODUCTIONS).toList(),
                payback(withLines), notices());
    }

    /** Every sale counts here, whatever its type: {@code SaleRules.total} zeroes the non-income ones. */
    private static BigDecimal previousPeriod(List<Sale> all, LocalDate today, YearMonth month) {
        YearMonth prev = month.minusMonths(1);
        LocalDate from = prev.atDay(1);
        LocalDate to = prev.atDay(Math.min(today.getDayOfMonth(), prev.lengthOfMonth()));
        return all.stream().filter(s -> !s.date().isBefore(from) && !s.date().isAfter(to))
                .map(SaleRules::total).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<PackSales> salesByPack(List<Sale> all, YearMonth month) {
        // one month = one period: each row is a (pack, branch) total of income sales
        Map<Long, List<Analysis.Row>> byPack = Analysis.rows(all, month.atDay(1), month.atEndOfMonth(),
                Analysis.GroupBy.MONTH).stream().collect(Collectors.groupingBy(Analysis.Row::packId));
        List<Branch> allBranches = branches.findAll();
        return packs.findAll().stream().map(p -> {
            List<Analysis.Row> rows = byPack.getOrDefault(p.id(), List.of());
            return new PackSales(p.id(), p.name(), rows.stream().mapToInt(Analysis.Row::quantity).sum(),
                    rows.stream().map(Analysis.Row::amount).reduce(BigDecimal.ZERO, BigDecimal::add),
                    allBranches.stream().map(b -> new BranchQuantity(b.id(), b.name(), rows.stream()
                            .filter(r -> r.branchId() == b.id()).mapToInt(Analysis.Row::quantity).sum())).toList());
        }).toList();
    }

    private static PaybackView payback(List<ProductionSummary> withLines) {
        return withLines.stream().filter(p -> p.status() == ProductionStatus.CLOSED)
                .max(Comparator.comparing(ProductionSummary::closedOn, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparingLong(ProductionSummary::id))
                .map(p -> new PaybackView(p.code(), p.payback().needed(), p.payback().missing())).orElse(null);
    }

    private Notices notices() {
        FollowUpsView f = customers.followUps();
        List<PackagingNotice> low = new ArrayList<>();
        PackagingStatus pk = purchases.packaging();
        if (!pk.tray().enough()) {
            low.add(new PackagingNotice(PackagingType.TRAY.code(), pk.tray()));
        }
        if (!pk.sticker().enough()) {
            low.add(new PackagingNotice(PackagingType.STICKER.code(), pk.sticker()));
        }
        return new Notices(
                f.followUps().stream().map(u -> new FollowUpNotice(u.customer().customer().id(),
                        u.customer().fullName(), u.daysWithoutPurchase(), u.packs())).toList(),
                f.birthdays().stream().map(c -> new BirthdayNotice(c.customer().id(), c.fullName(),
                        c.customer().phone())).toList(),
                low, purchases.pending().stream().map(v -> new PendingPurchase(v.purchase().id(), v.supplier(),
                v.production())).toList());
    }
}
