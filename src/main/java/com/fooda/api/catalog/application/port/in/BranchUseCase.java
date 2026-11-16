package com.fooda.api.catalog.application.port.in;

import com.fooda.api.core.domain.catalog.Branch;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.Page;

public interface BranchUseCase {
    Page<Branch> list(ListQuery q);
    Branch get(long id);
    Branch create(Branch draft);
    Branch update(long id, BranchChanges c);
    void delete(long id, long actorId);
}
