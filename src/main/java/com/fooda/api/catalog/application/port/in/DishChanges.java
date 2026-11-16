package com.fooda.api.catalog.application.port.in;

import com.fooda.api.core.domain.catalog.RecipeLine;
import com.fooda.api.shared.application.Field;

import java.util.List;

public record DishChanges(Field<String> name, Field<List<RecipeLine>> recipe, Field<Long> version) {
}
