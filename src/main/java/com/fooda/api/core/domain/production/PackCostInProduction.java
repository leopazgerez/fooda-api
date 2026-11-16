package com.fooda.api.core.domain.production;

import java.math.BigDecimal;

/** Costo directo de un pack: estimado (costos del snapshot) y real (con los precios pagados). */
public record PackCostInProduction(BigDecimal estimated, BigDecimal actual) {
}
