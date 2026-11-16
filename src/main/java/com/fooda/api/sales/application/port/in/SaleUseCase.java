package com.fooda.api.sales.application.port.in;

import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.Page;

public interface SaleUseCase {

    Page<SaleView> list(SaleFilter f, ListQuery q);

    SaleView get(long id);

    SaleView create(SaleRequest r);

    void annul(long id, long actorId);
}
