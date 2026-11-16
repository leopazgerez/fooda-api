package com.fooda.api.production.infrastructure.out.persistence;

import com.fooda.api.core.domain.catalog.Composite;
import com.fooda.api.core.domain.catalog.CompositeRule;
import com.fooda.api.core.domain.catalog.Dish;
import com.fooda.api.core.domain.catalog.MenuSlot;
import com.fooda.api.core.domain.catalog.PackagingType;
import com.fooda.api.core.domain.catalog.RecipeLine;
import com.fooda.api.core.domain.catalog.Settings;
import com.fooda.api.core.domain.catalog.Supply;
import com.fooda.api.core.domain.catalog.Unit;
import com.fooda.api.core.domain.production.ProductionSnapshot;
import com.fooda.api.core.domain.production.SnapshotPack;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Stored shape of production.snapshot (spec §5.7): plain records with enum codes, decoupled from the domain
 * records so a domain rename or new validation doesn't break rows already written.
 */
record SnapshotJson(Map<Long, BigDecimal> costs, Map<Long, DishJson> dishes, List<SnapshotPackJson> packs,
                    SettingsJson settings, List<SupplyJson> supplies) {

    record SupplyJson(Long id, String name, String unit, long supplierId, String presentation, BigDecimal content,
                      BigDecimal gramsPerUnit, String packagingType, CompositeJson composite, boolean active,
                      Long version) {
    }

    record CompositeJson(String rule, List<Long> partIds) {
    }

    record DishJson(Long id, String name, List<RecipeLineJson> recipe, Long version) {
    }

    record RecipeLineJson(long supplyId, BigDecimal quantity, String unit, BigDecimal portions) {
    }

    record SettingsJson(BigDecimal packPrice, BigDecimal oilMlPerPack, Long oilSupplyId, int traysPerPack,
                        int stickersPerPack, BigDecimal referenceOverhead, int regularMonths, int recontactDays,
                        BigDecimal priceRiseAlert, List<String> saleReasons, List<String> expenseTypes,
                        Long version) {
    }

    record SnapshotPackJson(long packId, long versionId, List<MenuSlotJson> menus) {
    }

    record MenuSlotJson(int number, long dishId) {
    }

    static SnapshotJson of(ProductionSnapshot s) {
        if (s == null) {
            return null;
        }
        Map<Long, DishJson> dishes = new LinkedHashMap<>();
        s.dishes().forEach((id, d) -> dishes.put(id, new DishJson(d.id(), d.name(), d.recipe().stream()
                .map(r -> new RecipeLineJson(r.supplyId(), r.quantity(), code(r.unit(), Unit::code), r.portions()))
                .toList(), d.version())));
        Settings t = s.settings();
        SettingsJson settings = t == null ? null : new SettingsJson(t.packPrice(), t.oilMlPerPack(), t.oilSupplyId(),
                t.traysPerPack(), t.stickersPerPack(), t.referenceOverhead(), t.regularMonths(), t.recontactDays(),
                t.priceRiseAlert(), t.saleReasons(), t.expenseTypes(), t.version());
        return new SnapshotJson(s.costs(), dishes,
                s.packs().stream().map(p -> new SnapshotPackJson(p.packId(), p.versionId(),
                        p.menus().stream().map(m -> new MenuSlotJson(m.number(), m.dishId())).toList())).toList(),
                settings,
                s.supplies().stream().map(x -> new SupplyJson(x.id(), x.name(), code(x.unit(), Unit::code),
                        x.supplierId(), x.presentation(), x.content(), x.gramsPerUnit(),
                        code(x.packagingType(), PackagingType::code),
                        x.composite() == null ? null : new CompositeJson(code(x.composite().rule(),
                                CompositeRule::code), x.composite().partIds()),
                        x.active(), x.version())).toList());
    }

    ProductionSnapshot toDomain() {
        Map<Long, Dish> ds = new LinkedHashMap<>();
        if (dishes != null) {
            dishes.forEach((id, d) -> ds.put(id, new Dish(d.id(), d.name(), d.recipe() == null ? null
                    : d.recipe().stream().map(r -> new RecipeLine(r.supplyId(), r.quantity(),
                    r.unit() == null ? null : Unit.of(r.unit(), "unit"), r.portions())).toList(), d.version())));
        }
        SettingsJson t = settings;
        Settings st = t == null ? null : new Settings(t.packPrice(), t.oilMlPerPack(), t.oilSupplyId(),
                t.traysPerPack(), t.stickersPerPack(), t.referenceOverhead(), t.regularMonths(), t.recontactDays(),
                t.priceRiseAlert(), t.saleReasons(), t.expenseTypes(), t.version());
        List<SnapshotPack> ps = packs == null ? null : packs.stream().map(p -> new SnapshotPack(p.packId(),
                p.versionId(), p.menus() == null ? null
                : p.menus().stream().map(m -> new MenuSlot(m.number(), m.dishId())).toList())).toList();
        List<Supply> ss = supplies == null ? null : supplies.stream().map(x -> new Supply(x.id(), x.name(),
                x.unit() == null ? null : Unit.of(x.unit(), "unit"), x.supplierId(), x.presentation(), x.content(),
                x.gramsPerUnit(), x.packagingType() == null ? null : PackagingType.of(x.packagingType(), "packagingType"),
                x.composite() == null ? null : new Composite(x.composite().rule() == null ? null
                        : CompositeRule.of(x.composite().rule(), "composite"),
                        x.composite().partIds()),
                x.active(), x.version())).toList();
        return new ProductionSnapshot(costs, ds, ps, st, ss);
    }

    private static <E> String code(E e, Function<E, String> code) {
        return e == null ? null : code.apply(e);
    }
}
