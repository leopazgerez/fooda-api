package com.fooda.api.shared.application;

import com.fooda.api.shared.domain.ValidationException;

public record ListQuery(int page, int size, String sort, String dir) {

    public ListQuery {
        if (page < 0) throw new ValidationException("page", "Página inválida");
        if (size < 1 || size > 200) throw new ValidationException("size", "Tamaño inválido");
        if (dir != null && !dir.equals("asc") && !dir.equals("desc")) {
            throw new ValidationException("dir", "Dirección inválida");
        }
    }
}
