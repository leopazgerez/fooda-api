package com.fooda.api.production.infrastructure.in.web;

import com.fooda.api.core.domain.production.PurchaseLine;
import com.fooda.api.shared.domain.ValidationException;
import com.fooda.api.shared.infrastructure.in.web.PatchBody;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/** Body parsing and 201 answers shared by the production web adapters. */
final class Web {

    private Web() {
    }

    static <T> ResponseEntity<T> created(long id, T body) {
        return ResponseEntity.created(
                ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri()).body(body);
    }

    /** The required list under {@code key}, each element parsed as an object. */
    static List<PatchBody> items(PatchBody b, String key) {
        return b.need(b.list(key), key).stream().map(o -> body(o, key)).toList();
    }

    /** Optional list; absent is empty. */
    static List<PatchBody> optionalItems(PatchBody b, String key) {
        var l = b.list(key).value();
        return l == null ? List.of() : l.stream().map(o -> body(o, key)).toList();
    }

    @SuppressWarnings("unchecked")
    static PatchBody body(Object o, String field) {
        if (!(o instanceof Map<?, ?> m)) {
            throw new ValidationException(field, "Valor inválido");
        }
        return new PatchBody((Map<String, Object>) m);
    }

    static long id(PatchBody b, String key) {
        return b.need(b.id(key), key);
    }

    static int integer(PatchBody b, String key) {
        return b.need(b.integer(key), key);
    }

    static LocalDate date(PatchBody b, String key) {
        String s = b.string(key).value();
        try {
            return s == null ? null : LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw new ValidationException(key, "Fecha inválida");
        }
    }

    /** Rounded to the column scales (3 and 2) so the total and paid costs match what gets stored. */
    static List<PurchaseLine> purchaseLines(PatchBody b) {
        return items(b, "lines").stream().map(l -> new PurchaseLine(id(l, "supplyId"),
                l.need(l.decimal("quantity"), "quantity").setScale(3, RoundingMode.HALF_UP),
                l.need(l.decimal("packagePrice"), "packagePrice").setScale(2, RoundingMode.HALF_UP))).toList();
    }
}
