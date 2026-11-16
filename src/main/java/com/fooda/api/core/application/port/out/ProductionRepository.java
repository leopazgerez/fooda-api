package com.fooda.api.core.application.port.out;

import com.fooda.api.core.domain.production.Production;

import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

public interface ProductionRepository {

    List<Production> findAll();

    /** Highest lot number started in the month, annulled ones included (codes are never reused); 0 if none. */
    int maxNumber(YearMonth month);

    Optional<Production> findById(long id);

    /** Saves the row; if the lines changed, soft-deletes the live lines and inserts the new ones. */
    Production save(Production p);

    /** Also soft-deletes its lines. */
    void softDelete(long id, long actorId);

    /** A live line of a live production has the pack. */
    boolean isPackUsed(long packId);
}
