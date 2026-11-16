package com.fooda.api.core.application.port.in;

import com.fooda.api.shared.application.Field;

public record SupplierChanges(Field<String> name, Field<String> purchaseDay, Field<Integer> sortOrder) {
}
