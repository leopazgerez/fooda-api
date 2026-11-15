package com.fooda.api.core.domain.catalog;

import com.fooda.api.shared.domain.ValidationException;

import java.util.List;

public record Dish(Long id, String name, List<RecipeLine> recipe, Long version) {

    public Dish {
        name = name == null ? "" : name.strip();
        if (name.isEmpty()) {
            throw new ValidationException("name", "Ingresá el nombre del plato");
        }
        if (recipe == null || recipe.isEmpty()) {
            throw new ValidationException("recipe", "La receta necesita al menos un ingrediente");
        }
        recipe = List.copyOf(recipe);
    }
}
