package com.fooda.api.production.application.port.in;

import java.util.List;

public record RecipeBookPack(long packId, String name, int quantity, List<RecipeBookMenu> menus) {
}
