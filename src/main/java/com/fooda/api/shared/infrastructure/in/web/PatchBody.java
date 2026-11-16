package com.fooda.api.shared.infrastructure.in.web;

import com.fooda.api.shared.application.Field;
import com.fooda.api.shared.domain.ValidationException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Reads a JSON body keeping "absent" and "null" apart; a value of the wrong type is a 400 on that key. */
public final class PatchBody {

    private static final Pattern WHOLE_NUMBER = Pattern.compile("-?\\d{1,19}");

    private final Map<String, Object> raw;

    public PatchBody(Map<String, Object> raw) {
        this.raw = raw == null ? Map.of() : raw;
    }

    public Field<String> string(String key) {
        return read(key, String.class, v -> v);
    }

    public Field<Boolean> bool(String key) {
        return read(key, Boolean.class, v -> v);
    }

    public Field<BigDecimal> decimal(String key) {
        return read(key, Number.class, v -> new BigDecimal(v.toString()));
    }

    /** A decimal rounded to cents. */
    public Field<BigDecimal> money(String key) {
        return decimal(key).map(v -> v.setScale(2, RoundingMode.HALF_UP));
    }

    public Field<Integer> integer(String key) {
        return read(key, Number.class, v -> whole(key, v).intValueExact());
    }

    public Field<Long> id(String key) {
        // ids come from <select>s, so a numeric string is as good as a number
        return read(key, Object.class, v -> {
            if (v instanceof Number n) {
                return whole(key, n).longValueExact();
            }
            if (v instanceof String t) {
                String text = t.trim();
                if (text.isEmpty()) {
                    return null; // a cleared <select> sends "": same as null
                }
                // plain integers only: BigDecimal would parse "1e999999999" and blow up in setScale
                if (WHOLE_NUMBER.matcher(text).matches()) {
                    return Long.parseLong(text);
                }
            }
            throw invalid(key);
        });
    }

    @SuppressWarnings("unchecked")
    public Field<Map<String, Object>> object(String key) {
        return read(key, Map.class, v -> (Map<String, Object>) v);
    }

    @SuppressWarnings("unchecked")
    public Field<List<Object>> list(String key) {
        return read(key, List.class, v -> (List<Object>) v);
    }

    /** PATCH of a required key: absent stays absent, an explicit null is a 400. */
    public <T> Field<T> required(Field<T> f, String key) {
        if (f.present() && f.value() == null) {
            throw new ValidationException(key, "Completá " + key);
        }
        return f;
    }

    /** POST/PUT of a required key: absent or null is a 400. */
    public <T> T need(Field<T> f, String key) {
        if (!f.present() || f.value() == null) {
            throw new ValidationException(key, "Completá " + key);
        }
        return f.value();
    }

    private static BigDecimal whole(String key, Number v) {
        try {
            return new BigDecimal(v.toString()).stripTrailingZeros().setScale(0);
        } catch (ArithmeticException e) {
            throw invalid(key);
        }
    }

    private <T, R> Field<R> read(String key, Class<T> type, java.util.function.Function<T, R> convert) {
        if (!raw.containsKey(key)) {
            return Field.absent();
        }
        Object v = raw.get(key);
        if (v == null) {
            return Field.of(null);
        }
        if (!type.isInstance(v)) {
            throw invalid(key);
        }
        try {
            return Field.of(convert.apply(type.cast(v)));
        } catch (NumberFormatException | ArithmeticException e) {
            throw invalid(key);
        }
    }

    private static ValidationException invalid(String key) {
        return new ValidationException(key, "Valor inválido");
    }
}
