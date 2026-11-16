package com.fooda.api.catalog.application.port.in;

import com.fooda.api.core.domain.catalog.Composite;
import com.fooda.api.core.domain.catalog.PackagingType;
import com.fooda.api.core.domain.catalog.Unit;
import com.fooda.api.shared.application.Field;

import java.math.BigDecimal;

public record SupplyChanges(Field<String> name, Field<Unit> unit, Field<Long> supplierId, Field<String> presentation,
                            Field<BigDecimal> content, Field<BigDecimal> gramsPerUnit,
                            Field<PackagingType> packagingType, Field<Composite> composite, Field<Boolean> active) {
}
