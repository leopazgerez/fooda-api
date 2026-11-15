package com.fooda.api.core.domain.catalog;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Port de porVigencia / versionVigente (fooda-admin produccion.ts). */
public final class PackVersions {

    public static final Comparator<PackVersion> BY_VALIDITY =
            Comparator.comparing(PackVersion::validFrom).thenComparing(PackVersion::id);

    private PackVersions() {
    }

    /** Versión vigente del pack al día: la última por vigencia con {@code validFrom} ≤ día. */
    public static Optional<PackVersion> current(List<PackVersion> versions, long packId, LocalDate day) {
        return versions.stream()
                .filter(v -> v.packId() == packId && !v.validFrom().isAfter(day))
                .max(BY_VALIDITY);
    }
}
