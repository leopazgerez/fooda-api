package com.fooda.api.core.application.port.out;

import com.fooda.api.core.domain.catalog.Branch;

import java.util.List;
import java.util.Optional;

public interface BranchRepository {

    List<Branch> findAll();

    Optional<Branch> findById(long id);

    Branch save(Branch value);

    void softDelete(long id, long actorId);

    Optional<Branch> findKitchen();
}
