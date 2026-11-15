package com.fooda.api.core.domain.catalog;

import com.fooda.api.shared.domain.ValidationException;

import java.time.LocalDate;
import java.util.List;

public record PackVersion(Long id, long packId, LocalDate validFrom, List<MenuSlot> menus) {

    public PackVersion {
        if (menus == null || menus.size() != 5) {
            throw new ValidationException("menus", "Un pack lleva exactamente 5 menús");
        }
        menus = List.copyOf(menus);
    }
}
