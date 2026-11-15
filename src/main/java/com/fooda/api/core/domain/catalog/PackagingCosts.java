package com.fooda.api.core.domain.catalog;

import java.math.BigDecimal;

/** Costo por unidad de bandeja y de sticker. */
public record PackagingCosts(BigDecimal tray, BigDecimal sticker) {
}
