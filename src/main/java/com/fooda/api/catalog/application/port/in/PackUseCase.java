package com.fooda.api.catalog.application.port.in;

import com.fooda.api.core.domain.catalog.MenuSlot;
import com.fooda.api.core.domain.catalog.PackVersion;

import java.time.LocalDate;
import java.util.List;

public interface PackUseCase {
    /** Ordered by number. */
    List<PackView> list();
    PackView get(long id);
    PackView create(String name);
    PackView update(long id, PackChanges c);
    void delete(long id, long actorId);
    List<PackVersion> versions(long packId);
    /** validFrom null = today. */
    PackVersion addVersion(long packId, LocalDate validFrom, List<MenuSlot> menus);
}
