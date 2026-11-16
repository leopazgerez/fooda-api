package com.fooda.api.sales.application.port.in;

import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.Page;

public interface CustomerUseCase {

    Page<CustomerView> list(ListQuery q);

    CustomerView get(long id);

    CustomerView create(CustomerInput in);

    CustomerView update(long id, CustomerInput in);

    void delete(long id, long actorId);

    FollowUpsView followUps();
}
