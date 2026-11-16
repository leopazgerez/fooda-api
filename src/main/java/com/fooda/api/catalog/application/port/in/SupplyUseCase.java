package com.fooda.api.catalog.application.port.in;

import com.fooda.api.core.domain.catalog.SupplyPrice;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.Page;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface SupplyUseCase {
    Page<SupplyView> list(ListQuery q);
    SupplyView get(long id);
    SupplyView create(NewSupply n);
    SupplyView update(long id, SupplyChanges c);
    void delete(long id, long actorId);
    List<SupplyPrice> prices(long supplyId);
    /** date null = today. */
    SupplyPrice addPrice(long supplyId, BigDecimal packagePrice, LocalDate date);
    void deletePrice(long supplyId, long priceId, long actorId);
}
