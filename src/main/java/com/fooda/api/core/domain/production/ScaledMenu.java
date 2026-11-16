package com.fooda.api.core.domain.production;

import com.fooda.api.core.domain.catalog.Unit;

import java.math.BigDecimal;
import java.util.List;

/** Menú del recetario; {@code sharedWith} y {@code combinedTotal} son nulos si ninguna otra línea usa el plato. */
public record ScaledMenu(int number, String dish, List<Ingredient> ingredients, SharedWith sharedWith,
                         Integer combinedTotal) {

    public ScaledMenu {
        ingredients = List.copyOf(ingredients);
    }

    public record Ingredient(String name, BigDecimal quantity, Unit unit) {
    }

    public record SharedWith(long packId, int quantity) {
    }
}
