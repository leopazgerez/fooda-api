package com.fooda.api.catalog.application.port.in;

import com.fooda.api.shared.application.Field;

import java.math.BigDecimal;

public record ReserveFundChanges(Field<String> name, Field<BigDecimal> percentage) {
}
