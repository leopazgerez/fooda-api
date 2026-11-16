package com.fooda.api.production.application.port.in;

import com.fooda.api.core.domain.production.PurchaseStatus;
import com.fooda.api.core.domain.production.ShoppingItem;

import java.math.BigDecimal;
import java.util.List;

/** {@code purchaseId} null and {@code PENDING} when the supplier has no purchase for the production yet. */
public record ShoppingListGroup(long supplierId, String supplier, List<ShoppingItem> items, BigDecimal total,
                                BigDecimal totalInPacks, Long purchaseId, PurchaseStatus status) {
}
