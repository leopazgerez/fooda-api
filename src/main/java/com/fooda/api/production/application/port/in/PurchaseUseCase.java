package com.fooda.api.production.application.port.in;

import com.fooda.api.core.domain.production.PurchaseLine;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.Page;

import java.time.LocalDate;
import java.util.List;

public interface PurchaseUseCase {

    /** {@code productionId} null = all purchases. */
    Page<PurchaseView> list(ListQuery q, Long productionId);

    PurchaseView get(long id);

    /** {@code date} null = today. */
    BoughtPurchase buy(long id, LocalDate date, List<PurchaseLine> lines);

    BoughtPurchase buyLoose(long supplierId, LocalDate date, List<PurchaseLine> lines);

    /** {@code actorId} soft-deletes the expense, the loose purchase or pending purchases that drop out. */
    void annul(long id, long actorId);

    PackagingStatus packaging();
}
