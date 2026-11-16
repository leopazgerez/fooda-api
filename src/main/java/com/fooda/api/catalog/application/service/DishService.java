package com.fooda.api.catalog.application.service;

import com.fooda.api.catalog.application.port.in.DishChanges;
import com.fooda.api.catalog.application.port.in.DishUseCase;
import com.fooda.api.catalog.application.port.in.DishView;
import com.fooda.api.core.application.port.out.DishRepository;
import com.fooda.api.core.application.port.out.PackRepository;
import com.fooda.api.core.application.port.out.SupplyRepository;
import com.fooda.api.core.domain.catalog.*;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.Listing;
import com.fooda.api.shared.application.Page;
import com.fooda.api.shared.application.exception.ResourceNotFoundException;
import com.fooda.api.shared.application.port.out.Transactions;
import com.fooda.api.shared.domain.ConflictException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DishService implements DishUseCase {

    private static final Comparator<DishView> BY_ID = Comparator.comparing(v -> v.dish().id());
    private static final Map<String, Comparator<DishView>> SORTABLE = Map.of(
            "name", Comparator.comparing(v -> v.dish().name(), String.CASE_INSENSITIVE_ORDER),
            "cost", Comparator.comparing(DishView::cost),
            "id", BY_ID);

    private final DishRepository dishes;
    private final PackRepository packs;
    private final SupplyRepository supplies;
    private final BusinessCalendar calendar;
    private final Transactions tx;

    public DishService(DishRepository dishes, PackRepository packs, SupplyRepository supplies,
                       BusinessCalendar calendar, Transactions tx) {
        this.dishes = dishes;
        this.packs = packs;
        this.supplies = supplies;
        this.calendar = calendar;
        this.tx = tx;
    }

    @Override
    public Page<DishView> list(ListQuery q) {
        Function<Dish, DishView> view = viewer();
        return Listing.page(dishes.findAll().stream().map(view).toList(), q, SORTABLE, "id", "asc", BY_ID);
    }

    @Override
    public DishView get(long id) {
        return viewer().apply(find(id));
    }

    @Override
    public DishView create(Dish draft) {
        return tx.get(() -> {
            checkSupplies(draft);
            return viewer().apply(dishes.save(new Dish(null, draft.name(), draft.recipe(), null)));
        });
    }

    @Override
    public DishView update(long id, DishChanges c) {
        return tx.get(() -> {
            Dish d = find(id);
            Dish next = new Dish(id, c.name().or(d.name()), c.recipe().or(d.recipe()), d.version());
            checkSupplies(next);
            return viewer().apply(dishes.save(next));
        });
    }

    @Override
    public void delete(long id, long actorId) {
        tx.run(() -> {
            find(id);
            if (packs.isDishUsed(id)) {
                throw new ConflictException(null, "El plato está en uso en un pack");
            }
            dishes.softDelete(id, actorId);
        });
    }

    private Dish find(long id) {
        return dishes.findById(id).orElseThrow(() -> new ResourceNotFoundException("Plato no encontrado"));
    }

    private void checkSupplies(Dish d) {
        for (RecipeLine l : d.recipe()) {
            supplies.findById(l.supplyId()).orElseThrow(() -> new ResourceNotFoundException("Insumo no encontrado"));
        }
    }

    /** Loads supplies, prices and current versions once and returns a dish → view mapper. */
    private Function<Dish, DishView> viewer() {
        LocalDate today = calendar.today();
        List<Supply> all = supplies.findAll();
        Map<Long, Supply> byId = all.stream().collect(Collectors.toMap(Supply::id, s -> s));
        Map<Long, BigDecimal> costs = Costs.currentCosts(all, supplies.findAllPrices(), today);
        List<PackVersion> versions = packs.findAllVersions();
        List<PackVersion> current = packs.findAll().stream()
                .sorted(Comparator.comparingInt(Pack::number))
                .flatMap(p -> PackVersions.current(versions, p.id(), today).stream())
                .toList();
        return d -> new DishView(d, Costs.dishCost(d, costs, byId), current.stream()
                .flatMap(v -> v.menus().stream().filter(m -> m.dishId() == d.id())
                        .map(m -> new DishView.MenuRef(m.number(), v.packId())))
                .toList());
    }
}
