package com.fooda.api.dashboard.application.service;

import com.fooda.api.catalog.fakes.InMemoryBranchRepository;
import com.fooda.api.catalog.fakes.InMemoryPackRepository;
import com.fooda.api.core.domain.catalog.Branch;
import com.fooda.api.core.domain.catalog.Pack;
import com.fooda.api.core.domain.catalog.Shipping;
import com.fooda.api.core.domain.catalog.ShippingType;
import com.fooda.api.core.domain.finance.AppliedOverhead;
import com.fooda.api.core.domain.finance.MonthlyReport;
import com.fooda.api.core.domain.finance.Payback;
import com.fooda.api.core.domain.production.ProductionStatus;
import com.fooda.api.core.domain.production.Purchase;
import com.fooda.api.core.domain.production.PurchaseStatus;
import com.fooda.api.core.domain.sales.Customer;
import com.fooda.api.core.domain.sales.Sale;
import com.fooda.api.core.domain.sales.SaleLine;
import com.fooda.api.core.domain.sales.SaleType;
import com.fooda.api.dashboard.application.port.in.DashboardView;
import com.fooda.api.finance.application.port.in.FinanceUseCase;
import com.fooda.api.finance.application.port.in.MonthlyView;
import com.fooda.api.production.application.port.in.PackagingStatus;
import com.fooda.api.production.application.port.in.ProductionSummary;
import com.fooda.api.production.application.port.in.ProductionUseCase;
import com.fooda.api.production.application.port.in.PurchaseUseCase;
import com.fooda.api.production.application.port.in.PurchaseView;
import com.fooda.api.sales.application.port.in.CustomerUseCase;
import com.fooda.api.sales.application.port.in.CustomerView;
import com.fooda.api.sales.application.port.in.FollowUpsView;
import com.fooda.api.sales.fakes.InMemorySaleRepository;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.stock.application.port.in.StockUseCase;
import com.fooda.api.stock.application.port.in.StockView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

    private final FinanceUseCase finance = mock(FinanceUseCase.class);
    private final StockUseCase stock = mock(StockUseCase.class);
    private final ProductionUseCase productions = mock(ProductionUseCase.class);
    private final PurchaseUseCase purchases = mock(PurchaseUseCase.class);
    private final CustomerUseCase customers = mock(CustomerUseCase.class);
    private final InMemorySaleRepository sales = new InMemorySaleRepository();
    private final InMemoryPackRepository packs = new InMemoryPackRepository();
    private final InMemoryBranchRepository branches = new InMemoryBranchRepository();
    private long kitchen, shop, pack1, pack2;

    @BeforeEach
    void setUp() {
        Shipping free = new Shipping(ShippingType.AMOUNT, BigDecimal.ZERO);
        kitchen = branches.save(new Branch(null, "Cocina", "x", 10, 1, 1, free, false, true, null)).id();
        shop = branches.save(new Branch(null, "Local", "y", 10, 1, 1, free, false, false, null)).id();
        pack1 = packs.save(new Pack(null, 1, "Pack 1", true, null, null)).id();
        pack2 = packs.save(new Pack(null, 2, "Pack 2", true, null, null)).id();
        when(finance.month(any())).thenAnswer(i -> new MonthlyView(report(i.getArgument(0)), null));
        when(stock.view(null, null, null)).thenReturn(new StockView(List.of(), List.of()));
        when(productions.summaries()).thenReturn(List.of());
        when(purchases.pending()).thenReturn(List.of());
        when(purchases.packaging()).thenReturn(new PackagingStatus(item(true), item(true)));
        when(customers.followUps()).thenReturn(new FollowUpsView(List.of(), List.of()));
    }

    private DashboardService at(String day) {
        Clock clock = Clock.fixed(Instant.parse(day + "T15:00:00Z"), java.time.ZoneOffset.UTC);
        return new DashboardService(finance, stock, productions, purchases, customers, sales, packs, branches,
                new BusinessCalendar(clock));
    }

    private static MonthlyReport report(YearMonth m) {
        BigDecimal z = BigDecimal.ZERO;
        return new MonthlyReport(m, 0, 0, z, z, z, z, z, new AppliedOverhead(z, false, false), z, z, z, List.of());
    }

    private static PackagingStatus.Item item(boolean enough) {
        return new PackagingStatus.Item(1L, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ONE, enough);
    }

    private void sale(String date, SaleType type, long branch, long pack, int qty, String price) {
        sales.add(new Sale(null, LocalDate.parse(date), branch, null, type, null, null, null, null, null,
                List.of(new SaleLine(1, pack, qty, new BigDecimal(price)))));
    }

    private static ProductionSummary prod(long id, ProductionStatus st, String closedOn, boolean lines) {
        var pl = lines ? List.of(new ProductionSummary.PackLine(1, "p", 1, 1, 1L, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE)) : List.<ProductionSummary.PackLine>of();
        return new ProductionSummary(id, "P" + id, (int) id, st, LocalDate.of(2026, 1, 1),
                closedOn == null ? null : LocalDate.parse(closedOn), 1, BigDecimal.ONE, BigDecimal.ONE, 0,
                BigDecimal.ZERO, new AppliedOverhead(BigDecimal.ZERO, false, false), pl,
                new Payback(10 + (int) id, (int) id), 0L);
    }

    @Test
    void previousMonthSamePeriodClampsToMonthEnd() {
        sale("2026-02-01", SaleType.RETAIL, shop, pack1, 1, "100");
        sale("2026-02-28", SaleType.RETAIL, shop, pack1, 1, "10");
        sale("2026-03-01", SaleType.RETAIL, shop, pack1, 1, "1000");
        sale("2026-01-31", SaleType.RETAIL, shop, pack1, 1, "5000");
        assertThat(at("2026-03-31").get().salesPreviousMonthSamePeriod()).isEqualByComparingTo("110");
    }

    @Test
    void previousMonthSamePeriodMidMonth() {
        sale("2026-10-01", SaleType.RETAIL, shop, pack1, 1, "100");
        sale("2026-10-16", SaleType.WHOLESALE, shop, pack1, 1, "10");
        sale("2026-10-16", SaleType.INTERNAL_USE, shop, pack1, 3, "999");
        sale("2026-10-17", SaleType.RETAIL, shop, pack1, 1, "1000");
        assertThat(at("2026-11-16").get().salesPreviousMonthSamePeriod()).isEqualByComparingTo("110");
    }

    @Test
    void salesByPackCoversEveryPackAndBranch() {
        sale("2026-11-02", SaleType.RETAIL, shop, pack1, 2, "100");
        sale("2026-11-03", SaleType.RETAIL, kitchen, pack1, 1, "100");
        sale("2026-11-03", SaleType.INTERNAL_USE, shop, pack1, 5, "100");
        sale("2026-10-30", SaleType.RETAIL, shop, pack1, 7, "100");
        List<DashboardView.PackSales> r = at("2026-11-16").get().salesByPack();

        assertThat(r).extracting(DashboardView.PackSales::packId).containsExactly(pack1, pack2);
        assertThat(r.get(0).quantity()).isEqualTo(3);
        assertThat(r.get(0).amount()).isEqualByComparingTo("300");
        assertThat(r.get(0).byBranch()).extracting(DashboardView.BranchQuantity::quantity).containsExactly(1, 2);
        assertThat(r.get(1).quantity()).isZero();
        assertThat(r.get(1).amount()).isEqualByComparingTo("0");
        assertThat(r.get(1).byBranch()).extracting(DashboardView.BranchQuantity::branchId)
                .containsExactly(kitchen, shop);
        assertThat(r.get(1).byBranch()).allSatisfy(b -> assertThat(b.quantity()).isZero());
    }

    @Test
    void lastFiveProductionsWithLinesNewestFirst() {
        List<ProductionSummary> all = new ArrayList<>();
        for (long i = 1; i <= 7; i++) {
            all.add(prod(i, ProductionStatus.PLANNED, null, i != 6));
        }
        when(productions.summaries()).thenReturn(all);
        assertThat(at("2026-11-16").get().productions()).extracting(ProductionSummary::id)
                .containsExactly(7L, 5L, 4L, 3L, 2L);
    }

    @Test
    void paybackOfTheLastClosedByCloseDate() {
        assertThat(at("2026-11-16").get().payback()).isNull();
        when(productions.summaries()).thenReturn(List.of(prod(1, ProductionStatus.CLOSED, "2026-11-10", true),
                prod(2, ProductionStatus.CLOSED, "2026-11-01", true),
                prod(3, ProductionStatus.PLANNED, null, true),
                prod(4, ProductionStatus.CLOSED, "2026-11-12", false)));
        assertThat(at("2026-11-16").get().payback()).isEqualTo(new DashboardView.PaybackView("P1", 11, 1));
    }

    @Test
    void noticesMapFollowUpsBirthdaysPackagingAndPendingPurchases() {
        var ana = new CustomerView(new Customer(4L, "Ana", "Paz", "1155", null, null, null, 0L), "Ana Paz", true,
                null, 0, BigDecimal.ZERO);
        when(customers.followUps()).thenReturn(new FollowUpsView(List.of(
                new FollowUpsView.FollowUpView(ana, LocalDate.of(2026, 10, 1), 46, List.of(1L), List.of("Pack 1"))),
                List.of(ana)));
        when(purchases.packaging()).thenReturn(new PackagingStatus(item(false), item(true)));
        when(purchases.pending()).thenReturn(List.of(new PurchaseView(
                new Purchase(9L, 1L, 2L, PurchaseStatus.PENDING, null, List.of(), null, 0L), "Super", "P1",
                BigDecimal.ONE, List.of())));

        DashboardView.Notices n = at("2026-11-16").get().notices();

        assertThat(n.followUps()).containsExactly(new DashboardView.FollowUpNotice(4, "Ana Paz", 46, List.of("Pack 1")));
        assertThat(n.birthdays()).containsExactly(new DashboardView.BirthdayNotice(4, "Ana Paz", "1155"));
        assertThat(n.lowPackaging()).extracting(DashboardView.PackagingNotice::type).containsExactly("tray");
        assertThat(n.pendingPurchases()).containsExactly(new DashboardView.PendingPurchase(9, "Super", "P1"));
    }
}
