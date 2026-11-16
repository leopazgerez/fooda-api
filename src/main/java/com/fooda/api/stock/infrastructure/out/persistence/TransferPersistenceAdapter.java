package com.fooda.api.stock.infrastructure.out.persistence;

import com.fooda.api.core.application.port.out.TransferRepository;
import com.fooda.api.core.domain.stock.Transfer;
import com.fooda.api.core.domain.stock.TransferLine;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class TransferPersistenceAdapter implements TransferRepository {

    private final TransferJpaRepository transfers;
    private final TransferLineJpaRepository lines;
    private final Clock clock;

    public TransferPersistenceAdapter(TransferJpaRepository transfers, TransferLineJpaRepository lines, Clock clock) {
        this.transfers = transfers;
        this.lines = lines;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Transfer add(Transfer t) {
        TransferJpaEntity e = new TransferJpaEntity();
        e.date = t.date();
        e.originBranchId = t.originBranchId();
        e.destinationBranchId = t.destinationBranchId();
        e.notes = t.notes();
        e.sourceProductionId = t.sourceProductionId();
        TransferJpaEntity saved = transfers.saveAndFlush(e);
        for (TransferLine l : t.lines()) {
            TransferLineJpaEntity r = new TransferLineJpaEntity();
            r.transferId = saved.id;
            r.productionId = l.productionId();
            r.packId = l.packId();
            r.quantity = l.quantity();
            lines.saveAndFlush(r);
        }
        return withLines(List.of(saved)).get(0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transfer> findBySourceProduction(long productionId) {
        return withLines(transfers.findBySourceProductionIdOrderById(productionId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transfer> findContainingProduction(long productionId) {
        return withLines(transfers.findContainingProduction(productionId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Transfer> findAll() {
        return withLines(transfers.findAllByOrderById());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Transfer> findById(long id) {
        return transfers.findById(id).map(e -> withLines(List.of(e)).get(0));
    }

    @Override
    @Transactional
    public void softDelete(long id, long actorId) {
        lines.softDeleteByTransfer(id, actorId, clock.instant());
        transfers.softDelete(id, actorId, clock.instant());
    }

    private List<Transfer> withLines(List<TransferJpaEntity> es) {
        if (es.isEmpty()) {
            return List.of();
        }
        Map<Long, List<TransferLine>> byTransfer = new HashMap<>();
        lines.findByTransferIdInOrderById(es.stream().map(e -> e.id).toList()).forEach(l ->
                byTransfer.computeIfAbsent(l.transferId, k -> new ArrayList<>())
                        .add(new TransferLine(l.productionId, l.packId, l.quantity)));
        return es.stream().map(e -> new Transfer(e.id, e.date, e.originBranchId, e.destinationBranchId, e.notes,
                e.sourceProductionId, byTransfer.getOrDefault(e.id, List.of()))).toList();
    }
}
