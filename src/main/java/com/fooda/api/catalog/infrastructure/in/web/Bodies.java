package com.fooda.api.catalog.infrastructure.in.web;

import com.fooda.api.core.domain.catalog.Composite;
import com.fooda.api.core.domain.catalog.CompositeRule;
import com.fooda.api.core.domain.catalog.RecipeLine;
import com.fooda.api.core.domain.catalog.Shipping;
import com.fooda.api.core.domain.catalog.ShippingType;
import com.fooda.api.core.domain.catalog.Unit;
import com.fooda.api.shared.domain.ValidationException;
import com.fooda.api.shared.infrastructure.in.web.PatchBody;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Parsing of nested JSON objects shared by the catalog controllers, and the 201 + Location answer. */
final class Bodies {

    private Bodies() {
    }

    static <T> ResponseEntity<T> created(long id, T body) {
        return ResponseEntity.created(
                ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri()).body(body);
    }

    /** Sub-resource without its own GET (prices, pack versions): Location is the collection just posted to. */
    static <T> ResponseEntity<T> createdInCollection(T body) {
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().build().toUri()).body(body);
    }

    /** No shipping key at all defaults to amount/0; a shipping object must carry both type and value. */
    static Shipping shipping(Map<String, Object> m) {
        if (m == null) {
            return new Shipping(ShippingType.AMOUNT, BigDecimal.ZERO);
        }
        var b = new PatchBody(m);
        String type = b.string("type").value();
        BigDecimal value = b.decimal("value").value();
        if (type == null || value == null) {
            throw new ValidationException("shipping", "Completá el tipo y el valor del envío");
        }
        return new Shipping(ShippingType.of(type, "shipping"), value);
    }

    static Composite composite(Map<String, Object> m) {
        var b = new PatchBody(m);
        return new Composite(CompositeRule.of(b.string("rule").value(), "composite"),
                longs(b.list("supplyIds").value(), "composite"));
    }

    static List<RecipeLine> recipe(List<Object> items) {
        if (items == null) {
            return null;
        }
        return items.stream().map(o -> {
            var b = new PatchBody(map(o, "recipe"));
            Long supplyId = b.id("supplyId").value();
            if (supplyId == null) {
                throw new ValidationException("recipe", "Valor inválido");
            }
            return new RecipeLine(supplyId, b.decimal("quantity").value(),
                    Unit.of(b.string("unit").value(), "recipe"), b.decimal("portions").value());
        }).toList();
    }

    static List<Map<String, Object>> maps(List<Object> items, String field) {
        return items == null ? null : items.stream().map(o -> map(o, field)).toList();
    }

    static List<String> strings(List<Object> items, String field) {
        return items == null ? null : items.stream().map(o -> {
            if (!(o instanceof String s)) {
                throw new ValidationException(field, "Valor inválido");
            }
            return s;
        }).toList();
    }

    private static List<Long> longs(List<Object> items, String field) {
        if (items == null) {
            return null;
        }
        return items.stream().map(o -> {
            Long v = new PatchBody(java.util.Collections.singletonMap(field, o)).id(field).value();
            if (v == null) {
                throw new ValidationException(field, "Valor inválido");
            }
            return v;
        }).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o, String field) {
        if (!(o instanceof Map<?, ?> m)) {
            throw new ValidationException(field, "Valor inválido");
        }
        return (Map<String, Object>) m;
    }

    static int intOr(PatchBody b, String key, int dflt) {
        Integer v = b.integer(key).value();
        return v == null ? dflt : v;
    }

    static boolean boolOrFalse(PatchBody b, String key) {
        return Boolean.TRUE.equals(b.bool(key).value());
    }
}
