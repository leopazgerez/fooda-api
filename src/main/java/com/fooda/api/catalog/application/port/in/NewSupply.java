package com.fooda.api.catalog.application.port.in;

import com.fooda.api.core.domain.catalog.Composite;
import com.fooda.api.core.domain.catalog.PackagingType;
import com.fooda.api.core.domain.catalog.Unit;

import java.math.BigDecimal;

/** packagePrice null = no first price. */
public record NewSupply(String name, Unit unit, long supplierId, String presentation, BigDecimal content,
                        BigDecimal gramsPerUnit, PackagingType packagingType, Composite composite,
                        BigDecimal packagePrice) {
}
