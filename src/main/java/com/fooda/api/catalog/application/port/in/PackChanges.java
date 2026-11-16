package com.fooda.api.catalog.application.port.in;

import com.fooda.api.shared.application.Field;

import java.math.BigDecimal;

public record PackChanges(Field<String> name, Field<Boolean> active, Field<BigDecimal> price) {
}
