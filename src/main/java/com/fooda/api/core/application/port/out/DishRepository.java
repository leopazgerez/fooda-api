package com.fooda.api.core.application.port.out;

import com.fooda.api.core.domain.catalog.Dish;

import java.util.List;
import java.util.Optional;

public interface DishRepository {

    List<Dish> findAll();

    Optional<Dish> findById(long id);

    /** Saves the row; if the recipe changed, soft-deletes the live lines and inserts the new ones in the given order. */
    Dish save(Dish d);

    /** Also soft-deletes its recipe lines. */
    void softDelete(long id, long actorId);

    /** True when the supply appears in a live recipe line of a live dish. */
    boolean isSupplyUsed(long supplyId);
}
