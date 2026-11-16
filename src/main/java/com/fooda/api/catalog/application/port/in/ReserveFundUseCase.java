package com.fooda.api.catalog.application.port.in;

import com.fooda.api.core.domain.catalog.ReserveFund;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.Page;

public interface ReserveFundUseCase {
    Page<ReserveFund> list(ListQuery q);
    ReserveFund get(long id);
    ReserveFund create(ReserveFund draft);
    ReserveFund update(long id, ReserveFundChanges c);
    void delete(long id, long actorId);
}
