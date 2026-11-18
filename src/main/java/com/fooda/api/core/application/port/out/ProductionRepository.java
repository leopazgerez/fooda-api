package com.fooda.api.core.application.port.out;

import com.fooda.api.core.domain.production.Production;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ProductionRepository {

    List<Production> findAll();

    /** Highest lot number started in the month, annulled ones included (codes are never reused); 0 if none. */
    int maxNumber(YearMonth month);

    Optional<Production> findById(long id);

    /** Live productions with those ids, by id; unknown ids are skipped. */
    List<Production> findAllById(Collection<Long> ids);

    /** Live productions closed in [from, to], by id. */
    List<Production> findClosedBetween(LocalDate from, LocalDate to);

    /** Months of live productions: of closedOn, or of start while not closed. */
    Set<YearMonth> months();

    /** The live INICIAL lot (number 0), if it was created. */
    Optional<Production> findInitial();

    /** Saves the row; if the lines changed, soft-deletes the live lines and inserts the new ones. */
    Production save(Production p);

    /** Also soft-deletes its lines. */
    void softDelete(long id, long actorId);

    /** A live line of a live production has the pack. */
    boolean isPackUsed(long packId);
}
