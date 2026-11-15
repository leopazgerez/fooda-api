package com.fooda.api.core.application.port.out;

import com.fooda.api.core.domain.catalog.Supplier;

import java.util.List;
import java.util.Optional;

public interface SupplierRepository {

    List<Supplier> findAll();

    Optional<Supplier> findById(long id);

    Supplier save(Supplier value);

    void softDelete(long id, long actorId);
}
