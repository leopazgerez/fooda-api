package com.fooda.api.production.application.port.in;

import java.math.BigDecimal;

/** Port de estadoEnvases: bought − consumed by closed productions; enough if it covers another one like the last. */
public record PackagingStatus(Item tray, Item sticker) {

    /** {@code supplyId} null when no supply is marked with that packaging type. */
    public record Item(Long supplyId, BigDecimal bought, BigDecimal consumed, BigDecimal stock, BigDecimal need,
                       boolean enough) {
    }
}
