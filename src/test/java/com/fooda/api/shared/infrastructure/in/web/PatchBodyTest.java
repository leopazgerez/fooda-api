package com.fooda.api.shared.infrastructure.in.web;

import com.fooda.api.shared.application.Field;
import com.fooda.api.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PatchBodyTest {

    @Test
    void distinguishesAbsentNullAndValue() {
        var raw = new HashMap<String, Object>(Map.of("name", "X"));
        raw.put("presentation", null);
        var b = new PatchBody(raw);
        assertThat(b.string("name")).isEqualTo(Field.of("X"));
        assertThat(b.string("presentation")).isEqualTo(Field.of(null));
        assertThat(b.string("other").present()).isFalse();
    }

    @Test
    void wrongTypeIs400WithField() {
        var b = new PatchBody(Map.of("capacity", "mucho"));
        assertThatThrownBy(() -> b.integer("capacity"))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.field()).isEqualTo("capacity"));
    }

    @Test
    void convertsNumbers() {
        var b = new PatchBody(Map.of("n", 3, "d", 2.5, "id", 7));
        assertThat(b.integer("n").value()).isEqualTo(3);
        assertThat(b.decimal("d").value()).isEqualByComparingTo(new BigDecimal("2.5"));
        assertThat(b.id("id").value()).isEqualTo(7L);
        assertThatThrownBy(() -> b.integer("d")).isInstanceOf(ValidationException.class);
    }
}
