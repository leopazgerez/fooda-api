package com.fooda.api.stock.application.service;

import com.fooda.api.core.application.port.out.BranchRepository;
import com.fooda.api.core.application.port.out.MonthCloseRepository;
import com.fooda.api.core.application.port.out.PackRepository;
import com.fooda.api.core.application.port.out.ProductionRepository;
import com.fooda.api.core.application.port.out.SettingsRepository;
import com.fooda.api.core.application.port.out.StockLock;
import com.fooda.api.core.application.port.out.StockMovementRepository;
import com.fooda.api.core.domain.catalog.Pack;
import com.fooda.api.core.domain.production.Production;
import com.fooda.api.core.domain.production.ProductionSnapshot;
import com.fooda.api.core.domain.production.ProductionStatus;
import com.fooda.api.core.domain.stock.MovementType;
import com.fooda.api.core.domain.stock.StockMovement;
import com.fooda.api.core.domain.stock.StockRules;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.shared.application.Lookups;
import com.fooda.api.shared.application.exception.ResourceNotFoundException;
import com.fooda.api.shared.application.port.out.Transactions;
import com.fooda.api.shared.domain.ConflictException;
import com.fooda.api.shared.domain.ValidationException;
import com.fooda.api.stock.application.port.in.AdjustmentRequest;
import com.fooda.api.stock.application.port.in.AdjustmentUseCase;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

public class AdjustmentService implements AdjustmentUseCase {

    private static final String INITIAL_CODE = "INICIAL";

    private final StockMovementRepository movements;
    private final BranchRepository branches;
    private final PackRepository packs;
    private final ProductionRepository productions;
    private final SettingsRepository settings;
    private final MonthCloseRepository monthCloses;
    private final StockLock stockLock;
    private final BusinessCalendar calendar;
    private final Transactions tx;

    public AdjustmentService(StockMovementRepository movements, BranchRepository branches, PackRepository packs,
                             ProductionRepository productions, SettingsRepository settings,
                             MonthCloseRepository monthCloses, StockLock stockLock, BusinessCalendar calendar,
                             Transactions tx) {
        this.movements = movements;
        this.branches = branches;
        this.packs = packs;
        this.productions = productions;
        this.settings = settings;
        this.monthCloses = monthCloses;
        this.stockLock = stockLock;
        this.calendar = calendar;
        this.tx = tx;
    }

    @Override
    public StockMovement create(AdjustmentRequest r) {
        return tx.get(() -> {
            stockLock.lock();
            long branchId = Lookups.found(r.branchId(), branches::findById, "Sede no encontrada").id();
            Pack pack = Lookups.found(r.packId(), packs::findById, "Pack no encontrado");
            if (r.quantity() == null || r.quantity() == 0) {
                throw new ValidationException("quantity", "La cantidad debe ser un entero distinto de cero");
            }
            if (Math.abs((long) r.quantity()) > StockRules.MAX_QUANTITY) {
                throw new ValidationException("quantity", "La cantidad es demasiado grande");
            }
            if (r.reason() == null || r.reason().isBlank()) {
                throw new ValidationException("reason", "Indicá el motivo del ajuste");
            }
            if (r.quantity() > 0 && !pack.active()) {
                throw new ConflictException("packId", "El pack está inactivo");
            }
            LocalDate day = calendar.pastOrToday(r.date());
            Production lot = r.productionId() == null ? initialLot() : closedLot(r.productionId());
            StockRules.requireAfterLotsClosed(List.of(lot), day);
            long productionId = lot.id();
            if (r.quantity() < 0 && StockRules.available(movements.findByBranchAndProductions(branchId, List.of(productionId)), branchId, productionId, r.packId(), day)
                    + r.quantity() < 0) {
                throw new ValidationException("quantity", "El ajuste deja el stock en negativo");
            }
            monthCloses.requireOpen(day);
            return movements.add(new StockMovement(null, day, branchId, productionId, r.packId(), r.quantity(),
                    MovementType.ADJUSTMENT, null, r.reason().strip()));
        });
    }

    @Override
    public void annul(long id, long actorId) {
        tx.run(() -> {
            stockLock.lock();
            StockMovement m = movements.findById(id).filter(x -> x.type() == MovementType.ADJUSTMENT)
                    .orElseThrow(() -> new ResourceNotFoundException("Ajuste no encontrado"));
            monthCloses.requireOpen(m.date());
            if (m.quantity() > 0 && StockRules.available(movements.findByBranchAndProductions(m.branchId(), List.of(m.productionId())), m.branchId(), m.productionId(),
                    m.packId(), m.date()) - m.quantity() < 0) {
                throw new ConflictException("quantity", "Borrar el ajuste deja el stock en negativo");
            }
            movements.softDelete(id, actorId);
        });
    }

    private Production closedLot(long id) {
        Production p = productions.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Producción no encontrada"));
        if (p.status() != ProductionStatus.CLOSED) {
            throw new ConflictException("productionId", "La producción no está cerrada");
        }
        return p;
    }

    private Production initialLot() {
        return productions.findInitial().orElseGet(() -> {
            LocalDate today = calendar.today();
            return productions.save(new Production(null, INITIAL_CODE, 0, ProductionStatus.CLOSED, today, today,
                    List.of(), ProductionSnapshot.empty(settings.require()),
                    null, null, null));
        });
    }
}
