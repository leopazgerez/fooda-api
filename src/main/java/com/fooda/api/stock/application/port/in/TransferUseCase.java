package com.fooda.api.stock.application.port.in;

import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.Page;

public interface TransferUseCase {

    Page<TransferView> list(ListQuery q);

    TransferView create(TransferRequest r);

    void annul(long id, long actorId);
}
