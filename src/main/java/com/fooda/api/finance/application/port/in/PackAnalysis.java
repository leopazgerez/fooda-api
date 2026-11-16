package com.fooda.api.finance.application.port.in;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PackAnalysis(LocalDate from, LocalDate to, String groupBy, List<Row> rows, List<Change> changes) {

    public record Row(String period, long packId, String pack, long branchId, String branch, int quantity,
                      BigDecimal amount) {
    }

    public record Change(long packId, String pack, LocalDate validFrom, long versionId) {
    }
}
