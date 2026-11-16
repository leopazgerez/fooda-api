package com.fooda.api.shared.infrastructure.in.web;

import com.fooda.api.shared.domain.ValidationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/** Body parsing and 201 answers shared by the web adapters. */
public final class RequestBodies {

    private RequestBodies() {
    }

    public static <T> ResponseEntity<T> created(long id, T body) {
        return ResponseEntity.created(
                ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(id).toUri()).body(body);
    }

    /** The required list under {@code key}, each element parsed as an object. */
    public static List<PatchBody> items(PatchBody b, String key) {
        return b.need(b.list(key), key).stream().map(o -> body(o, key)).toList();
    }

    /** Optional list; absent is empty. */
    public static List<PatchBody> optionalItems(PatchBody b, String key) {
        var l = b.list(key).value();
        return l == null ? List.of() : l.stream().map(o -> body(o, key)).toList();
    }

    @SuppressWarnings("unchecked")
    public static PatchBody body(Object o, String field) {
        if (!(o instanceof Map<?, ?> m)) {
            throw new ValidationException(field, "Valor inválido");
        }
        return new PatchBody((Map<String, Object>) m);
    }

    public static long id(PatchBody b, String key) {
        return b.need(b.id(key), key);
    }

    public static int integer(PatchBody b, String key) {
        return b.need(b.integer(key), key);
    }

    public static LocalDate date(PatchBody b, String key) {
        String s = b.string(key).value();
        try {
            return s == null ? null : LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            throw new ValidationException(key, "Fecha inválida");
        }
    }
}
