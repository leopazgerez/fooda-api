package com.fooda.api.catalog.infrastructure.in.web;

import com.fooda.api.catalog.application.port.in.SupplierChanges;
import com.fooda.api.catalog.application.port.in.SupplierUseCase;
import com.fooda.api.core.domain.catalog.Supplier;
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

/** The Supplier domain record is the response shape (id, name, purchaseDay, sortOrder, version). */
@RestController
@RequestMapping("/api/suppliers")
public class SuppliersController {

    private final SupplierUseCase suppliers;

    public SuppliersController(SupplierUseCase suppliers) {
        this.suppliers = suppliers;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CATALOG_READ')")
    public Page<Supplier> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
                               @RequestParam(required = false) String sort, @RequestParam(required = false) String dir) {
        return suppliers.list(new ListQuery(page, size, sort, dir));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CATALOG_READ')")
    public Supplier get(@PathVariable long id) {
        return suppliers.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    public ResponseEntity<Supplier> create(@RequestBody Map<String, Object> body) {
        var b = new PatchBody(body);
        Supplier s = suppliers.create(new Supplier(null, b.string("name").value(), b.string("purchaseDay").value(),
                Bodies.intOr(b, "sortOrder", 0), null));
        return Bodies.created(s.id(), s);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    public Supplier update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        var b = new PatchBody(body);
        return suppliers.update(id, new SupplierChanges(b.string("name"), b.string("purchaseDay"),
                b.required(b.integer("sortOrder"), "sortOrder"), b.required(b.id("version"), "version")));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        suppliers.delete(id, Principals.userId(jwt));
        return ResponseEntity.noContent().build();
    }
}
