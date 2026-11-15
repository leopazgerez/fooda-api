package com.fooda.api.core.domain.catalog;

import com.fooda.api.shared.domain.ValidationException;

import java.util.List;

public record Composite(CompositeRule rule, List<Long> partIds) {

    public Composite {
        if (partIds == null || partIds.isEmpty()) {
            throw new ValidationException("composite", "Un compuesto necesita al menos un insumo");
        }
        partIds = List.copyOf(partIds);
    }
}
