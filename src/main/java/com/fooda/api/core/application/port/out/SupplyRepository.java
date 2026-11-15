package com.fooda.api.core.application.port.out;

import com.fooda.api.core.domain.catalog.Supply;
import com.fooda.api.core.domain.catalog.SupplyPrice;

import java.util.List;
import java.util.Optional;

public interface SupplyRepository {

    List<Supply> findAll();

    Optional<Supply> findById(long id);

    boolean existsByNameIgnoreCase(String name, Long exceptId);

    /** Saves the row and replaces its parts: listed live parts stay, unlisted ones are soft-deleted, new ones inserted. */
    Supply save(Supply s);

    /** Also soft-deletes its parts. */
    void softDelete(long id, long actorId);

    boolean existsBySupplier(long supplierId);

    boolean isPartOfComposite(long supplyId);

    List<SupplyPrice> findAllPrices();

    /** Ordered by date, then id. */
    List<SupplyPrice> findPrices(long supplyId);

    SupplyPrice addPrice(SupplyPrice p);

    Optional<SupplyPrice> findPrice(long priceId);

    void softDeletePrice(long priceId, long actorId);
}
