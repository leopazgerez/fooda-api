package com.fooda.api.catalog.application.service;

import com.fooda.api.catalog.application.port.in.BranchChanges;
import com.fooda.api.catalog.application.port.in.BranchUseCase;
import com.fooda.api.core.application.port.out.BranchRepository;
import com.fooda.api.core.application.port.out.StockLock;
import com.fooda.api.core.application.port.out.StockMovementRepository;
import com.fooda.api.core.domain.catalog.Branch;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.Listing;
import com.fooda.api.shared.application.Page;
import com.fooda.api.shared.application.exception.ResourceNotFoundException;
import com.fooda.api.shared.application.port.out.Transactions;
import com.fooda.api.shared.domain.ConflictException;

import java.util.Comparator;
import java.util.Map;

public class BranchService implements BranchUseCase {

    private static final Comparator<Branch> BY_ID = Comparator.comparing(Branch::id);
    private static final Map<String, Comparator<Branch>> SORTABLE = Map.of(
            "id", BY_ID,
            "name", Comparator.comparing(Branch::name, String.CASE_INSENSITIVE_ORDER));

    private final BranchRepository branches;
    private final StockMovementRepository movements;
    private final StockLock stockLock;
    private final Transactions tx;

    public BranchService(BranchRepository branches, StockMovementRepository movements, StockLock stockLock,
                         Transactions tx) {
        this.branches = branches;
        this.movements = movements;
        this.stockLock = stockLock;
        this.tx = tx;
    }

    @Override
    public Page<Branch> list(ListQuery q) {
        return Listing.page(branches.findAll(), q, SORTABLE, "id", "asc", BY_ID);
    }

    @Override
    public Branch get(long id) {
        return branches.findById(id).orElseThrow(() -> new ResourceNotFoundException("Sede no encontrada"));
    }

    @Override
    public Branch create(Branch draft) {
        return tx.get(() -> {
            checkSingleKitchen(draft);
            return branches.save(draft);
        });
    }

    @Override
    public Branch update(long id, BranchChanges c) {
        return tx.get(() -> {
            Branch b = get(id);
            Branch changed = new Branch(b.id(), c.name().or(b.name()), c.address().or(b.address()),
                    c.capacity().or(b.capacity()), c.stockThreshold().or(b.stockThreshold()),
                    c.freeSlotsThreshold().or(b.freeSlotsThreshold()), c.shipping().or(b.shipping()),
                    c.affectsPrice().or(b.affectsPrice()), c.kitchen().or(b.kitchen()), c.version().or(b.version()));
            checkSingleKitchen(changed);
            return branches.save(changed);
        });
    }

    @Override
    public void delete(long id, long actorId) {
        tx.run(() -> {
            stockLock.lock();
            if (get(id).kitchen()) {
                throw new ConflictException(null, "No se puede borrar la sede cocina");
            }
            if (movements.isBranchUsed(id)) {
                throw new ConflictException(null, "La sede tiene movimientos de stock");
            }
            branches.softDelete(id, actorId);
        });
    }

    private void checkSingleKitchen(Branch b) {
        if (b.kitchen() && branches.findKitchen().filter(k -> !k.id().equals(b.id())).isPresent()) {
            throw new ConflictException("kitchen", "Ya hay una sede marcada como cocina");
        }
    }
}
