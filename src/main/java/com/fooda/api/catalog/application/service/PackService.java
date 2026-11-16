package com.fooda.api.catalog.application.service;

import com.fooda.api.catalog.application.port.in.PackChanges;
import com.fooda.api.catalog.application.port.in.PackUseCase;
import com.fooda.api.catalog.application.port.in.PackView;
import com.fooda.api.core.application.port.out.DishRepository;
import com.fooda.api.core.application.port.out.PackRepository;
import com.fooda.api.core.application.port.out.SettingsRepository;
import com.fooda.api.core.application.port.out.SupplyRepository;
import com.fooda.api.core.domain.catalog.*;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.shared.application.exception.ResourceNotFoundException;
import com.fooda.api.shared.application.port.out.Transactions;
import com.fooda.api.shared.domain.Decimals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PackService implements PackUseCase {

    private final PackRepository packs;
    private final DishRepository dishes;
    private final SupplyRepository supplies;
    private final SettingsRepository settings;
    private final BusinessCalendar calendar;
    private final Transactions tx;

    public PackService(PackRepository packs, DishRepository dishes, SupplyRepository supplies,
                       SettingsRepository settings, BusinessCalendar calendar, Transactions tx) {
        this.packs = packs;
        this.dishes = dishes;
        this.supplies = supplies;
        this.settings = settings;
        this.calendar = calendar;
        this.tx = tx;
    }

    @Override
    public List<PackView> list() {
        Function<Pack, PackView> view = viewer();
        return packs.findAll().stream().sorted(Comparator.comparingInt(Pack::number)).map(view).toList();
    }

    @Override
    public PackView get(long id) {
        return viewer().apply(find(id));
    }

    @Override
    public PackView create(String name) {
        return tx.get(() -> {
            Function<Pack, PackView> view = viewer(); // fails fast without settings, before writing
            return view.apply(packs.save(new Pack(null, packs.maxNumber() + 1, name, true, null, null)));
        });
    }

    @Override
    public PackView update(long id, PackChanges c) {
        return tx.get(() -> {
            Pack p = find(id);
            Function<Pack, PackView> view = viewer();
            return view.apply(packs.save(new Pack(id, p.number(), c.name().or(p.name()),
                    c.active().or(p.active()), c.price().or(p.price()), c.version().or(p.version()))));
        });
    }

    @Override
    public void delete(long id, long actorId) {
        tx.run(() -> {
            find(id);
            packs.softDelete(id, actorId);
        });
    }

    @Override
    public List<PackVersion> versions(long packId) {
        find(packId);
        return packs.findVersions(packId);
    }

    @Override
    public PackVersion addVersion(long packId, LocalDate validFrom, List<MenuSlot> menus) {
        return tx.get(() -> {
            find(packId);
            PackVersion v = new PackVersion(null, packId, calendar.orToday(validFrom), menus);
            for (MenuSlot m : v.menus()) {
                dishes.findById(m.dishId()).orElseThrow(() -> new ResourceNotFoundException("Plato no encontrado"));
            }
            return packs.addVersion(v);
        });
    }

    private Pack find(long id) {
        return packs.findById(id).orElseThrow(() -> new ResourceNotFoundException("Pack no encontrado"));
    }

    /** Loads settings, costs, dishes and versions once and returns a pack → view mapper. */
    private Function<Pack, PackView> viewer() {
        Settings s = settings.find()
                .orElseThrow(() -> new ResourceNotFoundException("Todavía no se cargaron los parámetros"));
        LocalDate today = calendar.today();
        List<Supply> all = supplies.findAll();
        Map<Long, Supply> byId = all.stream().collect(Collectors.toMap(Supply::id, x -> x));
        Map<Long, BigDecimal> costs = Costs.currentCosts(all, supplies.findAllPrices(), today);
        PackagingCosts packaging = Costs.packagingCosts(all, costs);
        Map<Long, Dish> dishById = dishes.findAll().stream().collect(Collectors.toMap(Dish::id, d -> d));
        List<PackVersion> versions = packs.findAllVersions();
        BigDecimal overhead = s.referenceOverhead() != null ? s.referenceOverhead() : BigDecimal.ZERO;
        return p -> {
            BigDecimal salePrice = p.salePrice(s);
            PackVersion v = PackVersions.current(versions, p.id(), today).orElse(null);
            PackView.CurrentVersion current = null;
            PackView.CostView cost = null;
            if (v != null) {
                current = new PackView.CurrentVersion(v, v.menus().stream().map(m -> {
                    Dish d = dishById.get(m.dishId());
                    return new PackView.MenuView(m.number(), m.dishId(), d != null ? d.name() : null,
                            d != null ? Costs.dishCost(d, costs, byId) : BigDecimal.ZERO);
                }).toList());
                PackCost c = Costs.packCost(v.menus(), dishById, costs, byId, s, packaging);
                cost = new PackView.CostView(c.recipes(), c.oil(), c.packaging(), c.direct(), overhead,
                        c.direct().add(overhead));
            }
            BigDecimal profit = salePrice.subtract(cost != null ? cost.total() : BigDecimal.ZERO);
            BigDecimal margin = salePrice.signum() > 0 ? profit.divide(salePrice, Decimals.MC) : BigDecimal.ZERO;
            return new PackView(p, salePrice, current, cost, profit, margin);
        };
    }
}
