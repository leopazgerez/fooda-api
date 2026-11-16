package com.fooda.api.core.application.port.out;

import com.fooda.api.core.domain.production.Purchase;

import java.util.List;
import java.util.Optional;

public interface PurchaseRepository {

    List<Purchase> findAll();

    List<Purchase> findByProduction(long productionId);

    Optional<Purchase> findById(long id);

    /** A given id is kept; if the lines changed, soft-deletes the live lines and inserts the new ones. */
    Purchase save(Purchase p);

    /** Also soft-deletes its lines. */
    void softDelete(long id, long actorId);

    /** A live line of a live purchase uses the supply. */
    boolean isSupplyUsed(long supplyId);

    /** A live purchase is from the supplier. */
    boolean isSupplierUsed(long supplierId);
}
