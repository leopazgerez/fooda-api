package com.fooda.api.dashboard.application.port.in;

import com.fooda.api.finance.application.port.in.MonthlyView;
import com.fooda.api.production.application.port.in.PackagingStatus;
import com.fooda.api.production.application.port.in.ProductionSummary;
import com.fooda.api.stock.application.port.in.StockView;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Port de la ruta /dashboard (rutas-reportes.ts). {@code payback} is null when no closed production has lines. */
public record DashboardView(LocalDate today, MonthlyView month, BigDecimal salesPreviousMonthSamePeriod,
                            List<StockView.BranchTotal> stockByBranch, List<PackSales> salesByPack,
                            List<ProductionSummary> productions, PaybackView payback, Notices notices) {

    public record PackSales(long packId, String pack, int quantity, BigDecimal amount, List<BranchQuantity> byBranch) {
    }

    public record BranchQuantity(long branchId, String branch, int quantity) {
    }

    public record PaybackView(String code, int needed, int missing) {
    }

    public record Notices(List<FollowUpNotice> followUps, List<BirthdayNotice> birthdays,
                          List<PackagingNotice> lowPackaging, List<PendingPurchase> pendingPurchases) {
    }

    public record FollowUpNotice(long customerId, String customer, long daysWithoutPurchase, List<String> packs) {
    }

    public record BirthdayNotice(long customerId, String customer, String phone) {
    }

    /** {@code type} is "tray" or "sticker". */
    public record PackagingNotice(String type, PackagingStatus.Item item) {
    }

    public record PendingPurchase(long purchaseId, String supplier, String production) {
    }
}
