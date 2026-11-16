package com.fooda.api.sales.application.port.in;

import com.fooda.api.shared.application.Field;

public record CustomerInput(Field<String> firstName, Field<String> lastName, Field<String> phone,
                            Field<String> address, Field<String> birthday, Field<String> notes,
                            Field<Long> version) {
}
