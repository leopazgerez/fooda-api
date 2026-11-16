package com.fooda.api.catalog.application.port.in;

import com.fooda.api.core.domain.catalog.Dish;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.Page;

public interface DishUseCase {
    Page<DishView> list(ListQuery q);
    DishView get(long id);
    DishView create(Dish draft);
    DishView update(long id, DishChanges c);
    void delete(long id, long actorId);
}
