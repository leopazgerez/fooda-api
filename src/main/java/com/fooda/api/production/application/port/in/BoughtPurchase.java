package com.fooda.api.production.application.port.in;

import java.util.List;

public record BoughtPurchase(PurchaseView purchase, List<PriceAlert> alerts) {

    public BoughtPurchase {
        alerts = List.copyOf(alerts);
    }
}
