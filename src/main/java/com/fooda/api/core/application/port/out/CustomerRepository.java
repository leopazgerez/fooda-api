package com.fooda.api.core.application.port.out;

import com.fooda.api.core.domain.sales.Customer;

import java.util.List;
import java.util.Optional;

public interface CustomerRepository {

    List<Customer> findAll();

    Optional<Customer> findById(long id);

    /** Insert, or update when the id is set; a stale version raises an optimistic-lock failure. */
    Customer save(Customer customer);

    void softDelete(long id, long actorId);
}
