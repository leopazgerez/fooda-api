package com.fooda.api.catalog.application.service;

import com.fooda.api.catalog.application.port.in.ReserveFundChanges;
import com.fooda.api.catalog.application.port.in.ReserveFundUseCase;
import com.fooda.api.core.application.port.out.ReserveFundRepository;
import com.fooda.api.core.domain.catalog.ReserveFund;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.Listing;
import com.fooda.api.shared.application.Page;
import com.fooda.api.shared.application.exception.ResourceNotFoundException;
import com.fooda.api.shared.application.port.out.Transactions;

import java.util.Comparator;
import java.util.Map;

public class ReserveFundService implements ReserveFundUseCase {

    private static final Comparator<ReserveFund> BY_ID = Comparator.comparing(ReserveFund::id);
    private static final Map<String, Comparator<ReserveFund>> SORTABLE = Map.of(
            "id", BY_ID,
            "name", Comparator.comparing(ReserveFund::name, String.CASE_INSENSITIVE_ORDER));

    private final ReserveFundRepository funds;
    private final Transactions tx;

    public ReserveFundService(ReserveFundRepository funds, Transactions tx) {
        this.funds = funds;
        this.tx = tx;
    }

    @Override
    public Page<ReserveFund> list(ListQuery q) {
        return Listing.page(funds.findAll(), q, SORTABLE, "id", "asc", BY_ID);
    }

    @Override
    public ReserveFund get(long id) {
        return funds.findById(id).orElseThrow(() -> new ResourceNotFoundException("Reserva no encontrada"));
    }

    @Override
    public ReserveFund create(ReserveFund draft) {
        return tx.get(() -> funds.save(draft));
    }

    @Override
    public ReserveFund update(long id, ReserveFundChanges c) {
        return tx.get(() -> {
            ReserveFund f = get(id);
            return funds.save(new ReserveFund(f.id(), c.name().or(f.name()), c.percentage().or(f.percentage()),
                    c.version().or(f.version())));
        });
    }

    @Override
    public void delete(long id, long actorId) {
        tx.run(() -> {
            get(id);
            funds.softDelete(id, actorId);
        });
    }
}
