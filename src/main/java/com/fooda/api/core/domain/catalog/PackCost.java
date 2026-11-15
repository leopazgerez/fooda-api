package com.fooda.api.core.domain.catalog;

import java.math.BigDecimal;

public record PackCost(BigDecimal recipes, BigDecimal oil, BigDecimal packaging, BigDecimal direct) {
}
