package com.fooda.api.production.infrastructure.out.persistence;

import com.fooda.api.core.domain.catalog.Composite;
import com.fooda.api.core.domain.catalog.CompositeRule;
import com.fooda.api.core.domain.catalog.MenuSlot;
import com.fooda.api.core.domain.catalog.PackagingType;
import com.fooda.api.core.domain.catalog.RecipeLine;
import com.fooda.api.core.domain.catalog.Unit;
import com.fooda.api.core.domain.production.ProductionSnapshot;
import com.fooda.api.core.domain.production.SnapshotPack;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Pins the stored production.snapshot format: if this literal stops reading, rows already in the DB break too. */
class SnapshotJsonTest {

    private static final String STORED = """
            {
              "costs": {"1": 1234.5678901234, "2": 0.0000001},
              "dishes": {"7": {"id": 7, "name": "Lasaña", "version": 2,
                "recipe": [{"supplyId": 1, "quantity": 100.500, "unit": "g", "portions": 2}]}},
              "packs": [{"packId": 3, "versionId": 9, "menus": [{"number": 1, "dishId": 7}]}],
              "settings": {"packPrice": 45000, "oilMlPerPack": 12.5, "oilSupplyId": null, "traysPerPack": 2,
                "stickersPerPack": 3, "referenceOverhead": 1000.50, "regularMonths": 6, "recontactDays": 30,
                "priceRiseAlert": 0.1, "saleReasons": ["promo"], "expenseTypes": ["fijo", "luz"], "version": 3},
              "supplies": [
                {"id": 1, "name": "Bandeja", "unit": "u", "supplierId": 5, "presentation": "x10", "content": 10,
                 "gramsPerUnit": null, "packagingType": "tray", "composite": null, "active": true, "version": 1},
                {"id": 4, "name": "Zapallo", "unit": "g", "supplierId": 5, "presentation": null, "content": 1,
                 "gramsPerUnit": null, "packagingType": null, "composite": {"rule": "max", "partIds": [2, 3]},
                 "active": true, "version": 0, "someFutureField": "ignored"}
              ]
            }""";

    @Test
    void readsTheStoredFormat() {
        ProductionSnapshot s = ProductionPersistenceAdapter.snapshot(STORED);

        assertThat(s.costs().get(1L)).isEqualByComparingTo("1234.5678901234");
        assertThat(s.dishes().get(7L).recipe()).containsExactly(
                new RecipeLine(1L, new BigDecimal("100.500"), Unit.G, new BigDecimal("2")));
        assertThat(s.packs()).containsExactly(new SnapshotPack(3L, 9L, List.of(new MenuSlot(1, 7L))));
        assertThat(s.settings().referenceOverhead()).isEqualByComparingTo("1000.50");
        assertThat(s.settings().expenseTypes()).containsExactly("fijo", "luz");
        assertThat(s.supplies()).hasSize(2);
        assertThat(s.supplies().get(0).unit()).isEqualTo(Unit.U);
        assertThat(s.supplies().get(0).packagingType()).isEqualTo(PackagingType.TRAY);
        assertThat(s.supplies().get(1).composite()).isEqualTo(new Composite(CompositeRule.MAX, List.of(2L, 3L)));
    }

    @Test
    void writesCodesAndPlainDecimals() {
        ProductionSnapshot s = ProductionPersistenceAdapter.snapshot(STORED);
        String json = ProductionPersistenceAdapter.JSON.writeValueAsString(SnapshotJson.of(s));

        assertThat(json).contains("\"unit\":\"u\"", "\"packagingType\":\"tray\"", "\"rule\":\"max\"",
                "0.0000001").doesNotContain("E-", "\"TRAY\"");
        assertThat(ProductionPersistenceAdapter.snapshot(json)).isEqualTo(s);
    }
}
