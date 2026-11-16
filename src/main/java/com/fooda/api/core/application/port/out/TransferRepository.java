package com.fooda.api.core.application.port.out;

import com.fooda.api.core.domain.stock.Transfer;

import java.util.List;
import java.util.Optional;

public interface TransferRepository {

    /** Inserts the transfer and its lines. */
    Transfer add(Transfer t);

    List<Transfer> findBySourceProduction(long productionId);

    /** Transfers with at least one line of that production. */
    List<Transfer> findContainingProduction(long productionId);

    /** Live transfers with their live lines, by id. */
    List<Transfer> findAll();

    Optional<Transfer> findById(long id);

    /** Also soft-deletes its lines. */
    void softDelete(long id, long actorId);
}
