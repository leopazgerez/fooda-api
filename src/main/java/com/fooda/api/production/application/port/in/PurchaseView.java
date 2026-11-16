package com.fooda.api.production.application.port.in;

import com.fooda.api.core.domain.catalog.Unit;
import com.fooda.api.core.domain.production.Purchase;

import java.math.BigDecimal;
import java.util.List;

/** Port de compraVista: {@code production} is the code (null for a loose purchase); {@code total} is the stored one once bought, else estimated. */
public record PurchaseView(Purchase purchase, String supplier, String production, BigDecimal total,
                           List<LineView> lines) {

    public PurchaseView {
        lines = List.copyOf(lines);
    }

    public record LineView(long supplyId, BigDecimal quantity, BigDecimal packagePrice, String supply, Unit unit,
                           BigDecimal content) {
    }
}
