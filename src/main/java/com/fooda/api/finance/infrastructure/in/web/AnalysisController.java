package com.fooda.api.finance.infrastructure.in.web;

import com.fooda.api.finance.application.port.in.AnalysisUseCase;
import com.fooda.api.finance.application.port.in.PackAnalysis;
import com.fooda.api.finance.application.port.in.VersionAt;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/analysis")
public class AnalysisController {

    private final AnalysisUseCase analysis;

    public AnalysisController(AnalysisUseCase analysis) {
        this.analysis = analysis;
    }

    @GetMapping("/packs")
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    public PackAnalysis packs(@RequestParam(required = false) LocalDate from,
                              @RequestParam(required = false) LocalDate to,
                              @RequestParam(required = false) String groupBy) {
        return analysis.packs(from, to, groupBy);
    }

    @GetMapping("/packs/{id}/version-at")
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    public VersionAt versionAt(@PathVariable long id, @RequestParam(required = false) LocalDate date) {
        return analysis.versionAt(id, date);
    }
}
