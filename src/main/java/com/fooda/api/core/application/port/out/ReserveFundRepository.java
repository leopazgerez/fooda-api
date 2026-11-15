package com.fooda.api.core.application.port.out;

import com.fooda.api.core.domain.catalog.ReserveFund;

import java.util.List;
import java.util.Optional;

public interface ReserveFundRepository {

    List<ReserveFund> findAll();

    Optional<ReserveFund> findById(long id);

    ReserveFund save(ReserveFund value);

    void softDelete(long id, long actorId);
}
