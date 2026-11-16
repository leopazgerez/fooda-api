package com.fooda.api.production.application.port.in;

import com.fooda.api.core.domain.production.ScaledMenu;

import java.util.List;

/** {@link ScaledMenu} with the live name of the pack that shares the dish. */
public record RecipeBookMenu(int number, String dish, List<ScaledMenu.Ingredient> ingredients, SharedWith sharedWith,
                             Integer combinedTotal) {

    /** {@code name} is the live pack name, or "—". */
    public record SharedWith(long packId, String name, int quantity) {
    }
}
