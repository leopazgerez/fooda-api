package com.fooda.api.finance.application.port.in;

import java.time.LocalDate;

public interface AnalysisUseCase {

    /** Null dates default to: to = today, from = first day of the month two months back. */
    PackAnalysis packs(LocalDate from, LocalDate to, String groupBy);

    /** Null date = today. */
    VersionAt versionAt(long packId, LocalDate date);
}
