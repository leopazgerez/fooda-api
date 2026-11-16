package com.fooda.api.sales.application.port.in;

import com.fooda.api.core.domain.sales.Payment;
import com.fooda.api.core.domain.sales.Sale;

import java.math.BigDecimal;
import java.util.List;

public record SaleView(Sale sale, String branch, String customer, int packs, List<LineView> lines, BigDecimal total,
                       BigDecimal profit, List<Payment> payments) {

    public record LineView(long productionId, String lot, long packId, String pack, int quantity,
                           BigDecimal unitPrice) {
    }
}
