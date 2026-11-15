package com.fooda.api.core.domain.catalog;

import com.fooda.api.shared.domain.ValidationException;

import java.math.BigDecimal;

public record Supply(Long id, String name, Unit unit, long supplierId, String presentation, BigDecimal content,
                     BigDecimal gramsPerUnit, PackagingType packagingType, Composite composite, boolean active,
                     Long version) {

    public Supply {
        name = name == null ? "" : name.strip();
        if (name.isEmpty()) {
            throw new ValidationException("name", "Ingresá el nombre del insumo");
        }
        if (unit == null) {
            unit = Unit.G;
        }
        if (content == null || content.signum() <= 0) {
            throw new ValidationException("content", "El contenido del envase debe ser mayor a cero");
        }
        if (gramsPerUnit != null && gramsPerUnit.signum() <= 0) {
            throw new ValidationException("gramsPerUnit", "Los gramos por unidad deben ser mayores a cero");
        }
        if (id != null && composite != null && composite.partIds().contains(id)) {
            throw new ValidationException("composite", "Un insumo no puede ser parte de sí mismo");
        }
    }
}
