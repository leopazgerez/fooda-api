package com.fooda.api.production.application.port.in;

import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.Page;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface ProductionUseCase {

    Page<ProductionSummary> list(ListQuery q);

    /** Every production, ordered by id. */
    List<ProductionSummary> summaries();

    ProductionDetail get(long id);

    /** {@code start} null = today; {@code actorId} soft-deletes pending purchases that drop out. */
    ProductionSummary plan(List<PlanLine> lines, LocalDate start, long actorId);

    List<ShoppingListGroup> shoppingList(long id);

    List<RecipeBookPack> recipeBook(long id);

    /** {@code version} null = the stored one; a stale one is a 409. */
    ProductionSummary setLeftovers(long id, Map<Long, BigDecimal> leftovers, Long version, long actorId);

    void annul(long id, long actorId);

    ProductionSummary close(long id, CloseRequest r);

    /** {@code actorId} soft-deletes the closing movements and the distribution transfers. */
    ProductionSummary reopen(long id, long actorId);

    record PlanLine(long packId, int planned) {
    }
}
