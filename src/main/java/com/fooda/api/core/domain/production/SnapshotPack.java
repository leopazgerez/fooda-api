package com.fooda.api.core.domain.production;

import com.fooda.api.core.domain.catalog.MenuSlot;

import java.util.List;

public record SnapshotPack(long packId, long versionId, List<MenuSlot> menus) {

    public SnapshotPack {
        menus = menus == null ? List.of() : List.copyOf(menus);
    }
}
