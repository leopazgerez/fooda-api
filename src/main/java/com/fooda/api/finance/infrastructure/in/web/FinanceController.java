package com.fooda.api.finance.infrastructure.in.web;

import com.fooda.api.finance.application.port.in.FinanceUseCase;
import com.fooda.api.finance.application.port.in.MonthlyResponse;
import com.fooda.api.shared.infrastructure.in.web.Principals;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/finance")
public class FinanceController {

    private final FinanceUseCase finance;

    public FinanceController(FinanceUseCase finance) {
        this.finance = finance;
    }

    @GetMapping("/monthly")
    @PreAuthorize("hasAuthority('FINANCE_READ')")
    public List<MonthlyResponse> monthly() {
        return finance.monthly().stream().map(MonthlyResponse::from).toList();
    }

    @PostMapping("/month-closes")
    @PreAuthorize("hasAuthority('FINANCE_WRITE')")
    public ResponseEntity<MonthlyResponse> close(@RequestBody Map<String, Object> body) {
        var r = MonthlyResponse.from(finance.close(body.get("month") instanceof String s ? s : null));
        return ResponseEntity.created(ServletUriComponentsBuilder.fromCurrentRequest().path("/{month}")
                .buildAndExpand(r.month()).toUri()).body(r);
    }

    @DeleteMapping("/month-closes/{month}")
    @PreAuthorize("hasAuthority('FINANCE_WRITE')")
    public ResponseEntity<Void> reopen(@AuthenticationPrincipal Jwt jwt, @PathVariable String month) {
        finance.reopen(month, Principals.userId(jwt));
        return ResponseEntity.noContent().build();
    }
}
