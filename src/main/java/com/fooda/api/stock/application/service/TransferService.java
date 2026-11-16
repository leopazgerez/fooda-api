package com.fooda.api.stock.application.service;

import com.fooda.api.core.application.port.out.BranchRepository;
import com.fooda.api.core.application.port.out.MonthCloseRepository;
import com.fooda.api.core.application.port.out.PackRepository;
import com.fooda.api.core.application.port.out.ProductionRepository;
import com.fooda.api.core.application.port.out.StockLock;
import com.fooda.api.core.application.port.out.StockMovementRepository;
import com.fooda.api.core.application.port.out.TransferRepository;
import com.fooda.api.core.domain.catalog.Branch;
import com.fooda.api.core.domain.catalog.Pack;
import com.fooda.api.core.domain.production.Production;
import com.fooda.api.core.domain.stock.MovementType;
import com.fooda.api.core.domain.stock.OutLine;
import com.fooda.api.core.domain.stock.Shortage;
import com.fooda.api.core.domain.stock.StockMovement;
import com.fooda.api.core.domain.stock.StockRules;
import com.fooda.api.core.domain.stock.Transfer;
import com.fooda.api.core.domain.stock.TransferLine;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.Listing;
import com.fooda.api.shared.application.Page;
import com.fooda.api.shared.application.exception.ResourceNotFoundException;
import com.fooda.api.shared.application.port.out.Transactions;
import com.fooda.api.shared.domain.ConflictException;
import com.fooda.api.shared.domain.ValidationException;
import com.fooda.api.stock.application.port.in.TransferRequest;
import com.fooda.api.stock.application.port.in.TransferUseCase;
import com.fooda.api.stock.application.port.in.TransferView;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.stream.Collectors;

public class TransferService implements TransferUseCase {

    private static final String UNKNOWN = "—";
    private static final Map<String, Comparator<Transfer>> SORTABLE = Map.of(
            "id", Comparator.comparingLong(Transfer::id),
            "date", Comparator.comparing(Transfer::date));

    private final TransferRepository transfers;
    private final StockMovementRepository movements;
    private final BranchRepository branches;
    private final PackRepository packs;
    private final ProductionRepository productions;
    private final MonthCloseRepository monthCloses;
    private final StockLock stockLock;
    private final BusinessCalendar calendar;
    private final Transactions tx;

    public TransferService(TransferRepository transfers, StockMovementRepository movements, BranchRepository branches,
                           PackRepository packs, ProductionRepository productions, MonthCloseRepository monthCloses,
                           StockLock stockLock, BusinessCalendar calendar, Transactions tx) {
        this.transfers = transfers;
        this.movements = movements;
        this.branches = branches;
        this.packs = packs;
        this.productions = productions;
        this.monthCloses = monthCloses;
        this.stockLock = stockLock;
        this.calendar = calendar;
        this.tx = tx;
    }

    @Override
    public Page<TransferView> list(ListQuery q) {
        Page<Transfer> page = Listing.page(transfers.findAll(), q, SORTABLE, "date", "desc",
                Comparator.comparingLong(Transfer::id));
        Names n = namesOf(page.items());
        return page.map(t -> view(t, n));
    }

    @Override
    public TransferView create(TransferRequest r) {
        return tx.get(() -> {
            stockLock.lock();
            Branch origin = branch(r.originBranchId());
            Branch destination = branch(r.destinationBranchId());
            if (origin.id().equals(destination.id())) {
                throw new ValidationException("destinationBranchId", "Origen y destino deben ser distintos");
            }
            if (r.lines() == null || r.lines().isEmpty()) {
                throw new ValidationException("lines", "Agregá al menos un pack");
            }
            Shortage s = StockRules.firstShortage(movements.findAll(), origin.id(), r.lines()).orElse(null);
            if (s != null) {
                throw new ValidationException("lines", "Solo hay %d packs de %s (%s) en %s"
                        .formatted(s.available(), packName(s.packId()), lotCode(s.productionId()), origin.name()));
            }
            LocalDate day = calendar.pastOrToday(r.date());
            boolean beforeALotClosed = r.lines().stream().map(OutLine::productionId).distinct()
                    .map(productions::findById).flatMap(Optional::stream)
                    .anyMatch(p -> !p.isInitial() && p.closedOn() != null && day.isBefore(p.closedOn()));
            if (beforeALotClosed) {
                throw new ValidationException("date", "La fecha no puede ser anterior al cierre del lote");
            }
            monthCloses.requireOpen(day);
            String notes = r.notes() == null || r.notes().isBlank() ? null : r.notes().strip();
            Transfer saved = transfers.add(new Transfer(null, day, origin.id(), destination.id(), notes, null,
                    r.lines().stream().map(l -> new TransferLine(l.productionId(), l.packId(), l.quantity()))
                            .toList()));
            for (OutLine l : r.lines()) {
                movements.add(new StockMovement(null, day, origin.id(), l.productionId(), l.packId(), -l.quantity(),
                        MovementType.TRANSFER_OUT, saved.id(), null));
                movements.add(new StockMovement(null, day, destination.id(), l.productionId(), l.packId(),
                        l.quantity(), MovementType.TRANSFER_IN, saved.id(), null));
            }
            return view(saved, namesOf(List.of(saved)));
        });
    }

    @Override
    public void annul(long id, long actorId) {
        tx.run(() -> {
            stockLock.lock();
            Transfer t = transfers.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Traslado no encontrado"));
            if (t.sourceProductionId() != null) {
                throw new ConflictException(null,
                        "Es un traslado de reparto: reabrí la producción o hacé un traslado inverso");
            }
            monthCloses.requireOpen(t.date());
            // returning the packs is an exit from the destination: same shortage rule as a new transfer
            List<OutLine> back = t.lines().stream()
                    .map(l -> new OutLine(l.productionId(), l.packId(), l.quantity())).toList();
            StockRules.firstShortage(movements.findAll(), t.destinationBranchId(), back).ifPresent(s -> {
                throw new ConflictException(null, "En %s ya no quedan %d packs de %s (%s)".formatted(
                        branchName(t.destinationBranchId()), s.requested(), packName(s.packId()),
                        lotCode(s.productionId())));
            });
            movements.findTransferLegs(id).forEach(m -> movements.softDelete(m.id(), actorId));
            transfers.softDelete(id, actorId);
        });
    }

    private record Names(Map<Long, String> branches, Map<Long, String> packs, Map<Long, String> lots) {
        String branch(long id) {
            return branches.getOrDefault(id, UNKNOWN);
        }

        String pack(long id) {
            return packs.getOrDefault(id, UNKNOWN);
        }

        String lot(long id) {
            return lots.getOrDefault(id, UNKNOWN);
        }
    }

    /** Names for just the ids these transfers mention (a page, or one transfer). */
    private Names namesOf(List<Transfer> ts) {
        Map<Long, String> branchNames = new HashMap<>();
        Map<Long, String> packNames = new HashMap<>();
        Map<Long, String> lotCodes = new HashMap<>();
        for (Transfer t : ts) {
            branchNames.computeIfAbsent(t.originBranchId(), this::branchName);
            branchNames.computeIfAbsent(t.destinationBranchId(), this::branchName);
            for (TransferLine l : t.lines()) {
                packNames.computeIfAbsent(l.packId(), this::packName);
                lotCodes.computeIfAbsent(l.productionId(), this::lotCode);
            }
        }
        return new Names(branchNames, packNames, lotCodes);
    }

    private TransferView view(Transfer t, Names n) {
        List<TransferView.LineView> lines = t.lines().stream()
                .map(l -> new TransferView.LineView(l.productionId(), n.lot(l.productionId()), l.packId(),
                        n.pack(l.packId()), l.quantity()))
                .toList();
        return new TransferView(t, n.branch(t.originBranchId()), n.branch(t.destinationBranchId()),
                t.lines().stream().mapToInt(TransferLine::quantity).sum(), lines);
    }

    private Branch branch(Long id) {
        if (id == null) {
            throw new ResourceNotFoundException("Sede no encontrada");
        }
        return branches.findById(id).orElseThrow(() -> new ResourceNotFoundException("Sede no encontrada"));
    }

    private String branchName(long id) {
        return branches.findById(id).map(Branch::name).orElse(UNKNOWN);
    }

    private String packName(long id) {
        return packs.findById(id).map(Pack::name).orElse(UNKNOWN);
    }

    private String lotCode(long id) {
        return productions.findById(id).map(Production::code).orElse(UNKNOWN);
    }
}
