package com.fooda.api.core.application.port.out;

import com.fooda.api.core.domain.sales.Sale;

import java.util.List;
import java.util.Optional;

public interface SaleRepository {

    /** Inserts the sale with its lines. */
    Sale add(Sale sale);

    List<Sale> findAll();

    Optional<Sale> findById(long id);

    boolean existsByCustomer(long customerId);

    /** Soft-deletes the sale and its lines. */
    void softDelete(long id, long actorId);
}
