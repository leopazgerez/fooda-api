package com.fooda.api.production.application.port.in;

import java.math.BigDecimal;

public record PriceAlert(long supplyId, String supply, BigDecimal previous, BigDecimal current, BigDecimal rise) {
}
