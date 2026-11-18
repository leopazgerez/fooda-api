package com.fooda.api.core.application.port.out;

import com.fooda.api.core.domain.sales.Sale;

import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.Page;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SaleRepository {

    /** Inserts the sale with its lines. */
    Sale add(Sale sale);

    List<Sale> findAll();

    /** Live sales dated in [from, to], by id. */
    List<Sale> findBetween(LocalDate from, LocalDate to);

    /** Live sales that have a customer, by id. */
    List<Sale> findWithCustomer();

    /** Months with a live sale. */
    Set<YearMonth> months();

    /** Filtered, sorted (date|id, asc|desc, ties by id) page; total counts all matches. Unknown sort -> ValidationException. */
    Page<Sale> page(SaleCriteria c, ListQuery q);

    Optional<Sale> findById(long id);

    boolean existsByCustomer(long customerId);

    /** Soft-deletes the sale and its lines. */
    void softDelete(long id, long actorId);
}
