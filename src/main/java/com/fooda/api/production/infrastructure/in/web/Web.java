package com.fooda.api.production.infrastructure.in.web;

import com.fooda.api.core.domain.production.PurchaseLine;
import com.fooda.api.shared.infrastructure.in.web.PatchBody;

import java.math.RoundingMode;
import java.util.List;

import static com.fooda.api.shared.infrastructure.in.web.RequestBodies.id;
import static com.fooda.api.shared.infrastructure.in.web.RequestBodies.items;

/** Production-specific body parsing. */
final class Web {

    private Web() {
    }

    /** Rounded to the column scales (3 and 2) so the total and paid costs match what gets stored. */
    static List<PurchaseLine> purchaseLines(PatchBody b) {
        return items(b, "lines").stream().map(l -> new PurchaseLine(id(l, "supplyId"),
                l.need(l.decimal("quantity"), "quantity").setScale(3, RoundingMode.HALF_UP),
                l.need(l.decimal("packagePrice"), "packagePrice").setScale(2, RoundingMode.HALF_UP))).toList();
    }
}
