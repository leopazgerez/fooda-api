package com.fooda.api.catalog.infrastructure.in.web;

import com.fooda.api.catalog.application.port.in.ReserveFundChanges;
import com.fooda.api.catalog.application.port.in.ReserveFundUseCase;
import com.fooda.api.core.domain.catalog.ReserveFund;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.Page;
import com.fooda.api.shared.infrastructure.in.web.PatchBody;
import com.fooda.api.shared.infrastructure.in.web.Principals;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** The ReserveFund domain record is the response shape (id, name, percentage, version). */
@RestController
@RequestMapping("/api/reserve-funds")
public class ReserveFundsController {

    private final ReserveFundUseCase funds;

    public ReserveFundsController(ReserveFundUseCase funds) {
        this.funds = funds;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CATALOG_READ')")
    public Page<ReserveFund> list(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "50") int size,
                                  @RequestParam(required = false) String sort,
                                  @RequestParam(required = false) String dir) {
        return funds.list(new ListQuery(page, size, sort, dir));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CATALOG_READ')")
    public ReserveFund get(@PathVariable long id) {
        return funds.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    public ResponseEntity<ReserveFund> create(@RequestBody Map<String, Object> body) {
        var b = new PatchBody(body);
        ReserveFund f = funds.create(new ReserveFund(null, b.string("name").value(), b.decimal("percentage").value(),
                null));
        return Bodies.created(f.id(), f);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    public ReserveFund update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        var b = new PatchBody(body);
        return funds.update(id, new ReserveFundChanges(b.string("name"), b.decimal("percentage")));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        funds.delete(id, Principals.userId(jwt));
        return ResponseEntity.noContent().build();
    }
}
