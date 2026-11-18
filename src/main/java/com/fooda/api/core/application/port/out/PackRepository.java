package com.fooda.api.core.application.port.out;

import com.fooda.api.core.domain.catalog.Pack;
import com.fooda.api.core.domain.catalog.PackVersion;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PackRepository {

    List<Pack> findAll();

    Optional<Pack> findById(long id);

    /** Live packs with those ids, ordered like {@link #findAll()}; unknown ids are skipped. */
    List<Pack> findAllById(Collection<Long> ids);

    Pack save(Pack p);

    /** 0 when there are no packs. */
    int maxNumber();

    /** Also soft-deletes its versions and their menus. */
    void softDelete(long id, long actorId);

    List<PackVersion> findAllVersions();

    /** Ordered by {@code PackVersions.BY_VALIDITY}. */
    List<PackVersion> findVersions(long packId);

    /** Menu position is the 1..5 index in the list. */
    PackVersion addVersion(PackVersion v);

    /** True when the dish is in a live menu of a version of a live pack. */
    boolean isDishUsed(long dishId);
}
