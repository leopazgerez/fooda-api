package com.fooda.api.core.domain.catalog;

import com.fooda.api.shared.domain.ValidationException;

import java.math.BigDecimal;
import java.util.List;

public record Settings(BigDecimal packPrice, BigDecimal oilMlPerPack, Long oilSupplyId, int traysPerPack,
                       int stickersPerPack, BigDecimal referenceOverhead, int regularMonths, int recontactDays,
                       BigDecimal priceRiseAlert, List<String> saleReasons, List<String> expenseTypes,
                       Long version) {

    public static final String FIXED_EXPENSE_TYPE = "fijo";

    public Settings {
        if (packPrice == null || packPrice.signum() <= 0) {
            throw new ValidationException("packPrice", "El precio del pack debe ser mayor a cero");
        }
        saleReasons = saleReasons == null ? List.of() : List.copyOf(saleReasons);
        expenseTypes = expenseTypes == null ? List.of() : List.copyOf(expenseTypes);
        if (!expenseTypes.contains(FIXED_EXPENSE_TYPE)) {
            throw new ValidationException("expenseTypes",
                    "El tipo \"fijo\" no se puede quitar: con él se calcula el indirecto por pack");
        }
    }
}
