package com.fooda.api.core.domain.production;

import java.util.List;

public record ScaledPack(long packId, int quantity, List<ScaledMenu> menus) {

    public ScaledPack {
        menus = List.copyOf(menus);
    }
}
