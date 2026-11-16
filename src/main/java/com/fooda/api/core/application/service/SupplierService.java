package com.fooda.api.core.application.service;

import com.fooda.api.core.application.port.in.SupplierChanges;
import com.fooda.api.core.application.port.in.SupplierUseCase;
import com.fooda.api.core.application.port.out.SupplierRepository;
import com.fooda.api.core.application.port.out.SupplyRepository;
import com.fooda.api.core.domain.catalog.Supplier;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.Listing;
import com.fooda.api.shared.application.Page;
import com.fooda.api.shared.application.exception.ResourceNotFoundException;
import com.fooda.api.shared.application.port.out.Transactions;
import com.fooda.api.shared.domain.ConflictException;

import java.util.Comparator;
import java.util.Map;

public class SupplierService implements SupplierUseCase {

    private static final Comparator<Supplier> BY_ID = Comparator.comparing(Supplier::id);
    private static final Map<String, Comparator<Supplier>> SORTABLE = Map.of(
            "sortOrder", Comparator.comparingInt(Supplier::sortOrder),
            "name", Comparator.comparing(Supplier::name, String.CASE_INSENSITIVE_ORDER),
            "id", BY_ID);

    private final SupplierRepository suppliers;
    private final SupplyRepository supplies;
    private final Transactions tx;

    public SupplierService(SupplierRepository suppliers, SupplyRepository supplies, Transactions tx) {
        this.suppliers = suppliers;
        this.supplies = supplies;
        this.tx = tx;
    }

    @Override
    public Page<Supplier> list(ListQuery q) {
        return Listing.page(suppliers.findAll(), q, SORTABLE, "sortOrder", "asc", BY_ID);
    }

    @Override
    public Supplier get(long id) {
        return suppliers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Proveedor no encontrado"));
    }

    @Override
    public Supplier create(Supplier draft) {
        return tx.get(() -> suppliers.save(draft));
    }

    @Override
    public Supplier update(long id, SupplierChanges c) {
        return tx.get(() -> {
            Supplier s = get(id);
            return suppliers.save(new Supplier(s.id(), c.name().or(s.name()), c.purchaseDay().or(s.purchaseDay()),
                    c.sortOrder().or(s.sortOrder()), s.version()));
        });
    }

    @Override
    public void delete(long id, long actorId) {
        tx.run(() -> {
            get(id);
            if (supplies.existsBySupplier(id)) {
                throw new ConflictException(null, "El proveedor tiene insumos cargados");
            }
            suppliers.softDelete(id, actorId);
        });
    }
}
