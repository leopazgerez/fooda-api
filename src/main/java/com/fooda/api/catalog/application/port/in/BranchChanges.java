package com.fooda.api.catalog.application.port.in;

import com.fooda.api.core.domain.catalog.Shipping;
import com.fooda.api.shared.application.Field;

public record BranchChanges(Field<String> name, Field<String> address, Field<Integer> capacity,
                            Field<Integer> stockThreshold, Field<Integer> freeSlotsThreshold, Field<Shipping> shipping,
                            Field<Boolean> affectsPrice, Field<Boolean> kitchen, Field<Long> version) {
}
