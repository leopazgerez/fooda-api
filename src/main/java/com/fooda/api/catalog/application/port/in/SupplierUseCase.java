package com.fooda.api.catalog.application.port.in;

import com.fooda.api.core.domain.catalog.Supplier;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.Page;

public interface SupplierUseCase {
    Page<Supplier> list(ListQuery q);
    Supplier get(long id);
    Supplier create(Supplier draft);
    Supplier update(long id, SupplierChanges c);
    void delete(long id, long actorId);
}
