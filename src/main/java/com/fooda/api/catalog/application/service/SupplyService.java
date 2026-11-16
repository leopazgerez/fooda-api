package com.fooda.api.catalog.application.service;

import com.fooda.api.catalog.application.port.in.*;
import com.fooda.api.core.application.port.out.DishRepository;
import com.fooda.api.core.application.port.out.SettingsRepository;
import com.fooda.api.core.application.port.out.SupplierRepository;
import com.fooda.api.core.application.port.out.SupplyRepository;
import com.fooda.api.core.domain.catalog.*;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.Listing;
import com.fooda.api.shared.application.Page;
import com.fooda.api.shared.application.exception.ResourceNotFoundException;
import com.fooda.api.shared.application.port.out.Transactions;
import com.fooda.api.shared.domain.ConflictException;
import com.fooda.api.shared.domain.ValidationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class SupplyService implements SupplyUseCase {

    private static final Comparator<SupplyView> BY_ID = Comparator.comparing(v -> v.supply().id());
    private static final Map<String, Comparator<SupplyView>> SORTABLE = Map.of(
            "name", Comparator.comparing(v -> v.supply().name(), String.CASE_INSENSITIVE_ORDER),
            "supplier", Comparator.comparing(SupplyView::supplierName, String.CASE_INSENSITIVE_ORDER),
            "unitCost", Comparator.comparing(SupplyView::unitCost),
            "id", BY_ID);

    private final SupplyRepository supplies;
    private final SupplierRepository suppliers;
    private final DishRepository dishes;
    private final SettingsRepository settings;
    private final BusinessCalendar calendar;
    private final Transactions tx;

    public SupplyService(SupplyRepository supplies, SupplierRepository suppliers, DishRepository dishes,
                         SettingsRepository settings, BusinessCalendar calendar, Transactions tx) {
        this.supplies = supplies;
        this.suppliers = suppliers;
        this.dishes = dishes;
        this.settings = settings;
        this.calendar = calendar;
        this.tx = tx;
    }

    @Override
    public Page<SupplyView> list(ListQuery q) {
        List<Supply> all = supplies.findAll();
        List<SupplyPrice> prices = supplies.findAllPrices();
        LocalDate today = calendar.today();
        Map<Long, BigDecimal> costs = Costs.currentCosts(all, prices, today);
        Map<Long, String> names = new java.util.HashMap<>();
        suppliers.findAll().forEach(s -> names.put(s.id(), s.name()));
        List<SupplyView> views = all.stream().map(s -> view(s, names.getOrDefault(s.supplierId(), ""),
                costs.get(s.id()), prices, today)).toList();
        return Listing.page(views, q, SORTABLE, "name", "asc", BY_ID);
    }

    @Override
    public SupplyView get(long id) {
        return view(find(id));
    }

    @Override
    public SupplyView create(NewSupply n) {
        return tx.get(() -> {
            Supply draft = new Supply(null, n.name(), n.unit(), n.supplierId(), n.presentation(), n.content(),
                    n.gramsPerUnit(), n.packagingType(), n.composite(), true, null);
            validate(draft, null);
            Supply saved = supplies.save(draft);
            if (n.packagePrice() != null) {
                supplies.addPrice(new SupplyPrice(null, saved.id(), calendar.today(), n.packagePrice()));
            }
            return view(saved);
        });
    }

    @Override
    public SupplyView update(long id, SupplyChanges c) {
        return tx.get(() -> {
            Supply s = find(id);
            Supply next = new Supply(id, c.name().or(s.name()), c.unit().or(s.unit()),
                    c.supplierId().or(s.supplierId()), c.presentation().or(s.presentation()),
                    c.content().or(s.content()), c.gramsPerUnit().or(s.gramsPerUnit()),
                    c.packagingType().or(s.packagingType()), c.composite().or(s.composite()),
                    c.active().or(s.active()), s.version());
            validate(next, id);
            return view(supplies.save(next));
        });
    }

    @Override
    public void delete(long id, long actorId) {
        tx.run(() -> {
            find(id);
            boolean isOil = settings.find().map(s -> Long.valueOf(id).equals(s.oilSupplyId())).orElse(false);
            if (dishes.isSupplyUsed(id) || supplies.isPartOfComposite(id) || isOil) {
                throw new ConflictException(null, "El insumo está en uso; desactivalo en lugar de borrarlo");
            }
            supplies.softDelete(id, actorId);
        });
    }

    @Override
    public List<SupplyPrice> prices(long supplyId) {
        find(supplyId);
        return supplies.findPrices(supplyId);
    }

    @Override
    public SupplyPrice addPrice(long supplyId, BigDecimal packagePrice, LocalDate date) {
        return tx.get(() -> {
            find(supplyId);
            return supplies.addPrice(new SupplyPrice(null, supplyId, calendar.orToday(date), packagePrice));
        });
    }

    @Override
    public void deletePrice(long supplyId, long priceId, long actorId) {
        tx.run(() -> {
            find(supplyId);
            supplies.findPrice(priceId).filter(p -> p.supplyId() == supplyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Precio no encontrado"));
            supplies.softDeletePrice(priceId, actorId);
        });
    }

    private Supply find(long id) {
        return supplies.findById(id).orElseThrow(() -> new ResourceNotFoundException("Insumo no encontrado"));
    }

    private void validate(Supply s, Long id) {
        if (supplies.existsByNameIgnoreCase(s.name(), id)) {
            throw new ConflictException("name", s.name() + " ya existe");
        }
        if (suppliers.findById(s.supplierId()).isEmpty()) {
            throw new ResourceNotFoundException("Proveedor no encontrado");
        }
        if (s.composite() != null) {
            for (long partId : s.composite().partIds()) {
                if (find(partId).composite() != null) {
                    throw new ValidationException("composite", "Las partes de un compuesto no pueden ser compuestas");
                }
            }
            if (id != null && supplies.isPartOfComposite(id)) {
                throw new ValidationException("composite", "Las partes de un compuesto no pueden ser compuestas");
            }
        }
        if (s.packagingType() != null && supplies.findAll().stream()
                .anyMatch(o -> o.packagingType() == s.packagingType() && !o.id().equals(id))) {
            throw new ConflictException("packagingType", "Ya hay un insumo marcado como ese envase");
        }
    }

    private SupplyView view(Supply s) {
        LocalDate today = calendar.today();
        List<SupplyPrice> prices = supplies.findAllPrices();
        BigDecimal cost = Costs.currentCosts(supplies.findAll(), prices, today).get(s.id());
        String supplier = suppliers.findById(s.supplierId()).map(Supplier::name).orElse("");
        return view(s, supplier, cost, prices, today);
    }

    private static SupplyView view(Supply s, String supplier, BigDecimal cost, List<SupplyPrice> prices,
                                   LocalDate today) {
        BigDecimal price = Costs.latestPrice(s.id(), prices, today).map(SupplyPrice::packagePrice).orElse(null);
        return new SupplyView(s, supplier, cost, price);
    }
}
