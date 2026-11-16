package com.fooda.api.catalog.infrastructure.in.web;

import com.fooda.api.catalog.application.port.in.BranchChanges;
import com.fooda.api.catalog.application.port.in.BranchUseCase;
import com.fooda.api.core.domain.catalog.Branch;
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

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/branches")
public class BranchesController {

    public record ShippingResponse(String type, BigDecimal value) {
    }

    public record BranchResponse(long id, String name, String address, int capacity, int stockThreshold,
                                 int freeSlotsThreshold, ShippingResponse shipping, boolean affectsPrice,
                                 boolean kitchen, Long version) {
        static BranchResponse from(Branch b) {
            return new BranchResponse(b.id(), b.name(), b.address(), b.capacity(), b.stockThreshold(),
                    b.freeSlotsThreshold(), new ShippingResponse(b.shipping().type().code(), b.shipping().value()),
                    b.affectsPrice(), b.kitchen(), b.version());
        }
    }

    private final BranchUseCase branches;

    public BranchesController(BranchUseCase branches) {
        this.branches = branches;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CATALOG_READ')")
    public Page<BranchResponse> list(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size,
                                     @RequestParam(required = false) String sort,
                                     @RequestParam(required = false) String dir) {
        return branches.list(new ListQuery(page, size, sort, dir)).map(BranchResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CATALOG_READ')")
    public BranchResponse get(@PathVariable long id) {
        return BranchResponse.from(branches.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    public ResponseEntity<BranchResponse> create(@RequestBody Map<String, Object> body) {
        var b = new PatchBody(body);
        var created = BranchResponse.from(branches.create(new Branch(null, b.string("name").value(),
                b.string("address").value(), Bodies.intOr0(b, "capacity"), Bodies.intOr0(b, "stockThreshold"),
                Bodies.intOr0(b, "freeSlotsThreshold"), Bodies.shipping(b.object("shipping").value()),
                Bodies.boolOrFalse(b, "affectsPrice"), Bodies.boolOrFalse(b, "kitchen"), null)));
        return Bodies.created(created.id(), created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    public BranchResponse update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        var b = new PatchBody(body);
        return BranchResponse.from(branches.update(id, new BranchChanges(b.string("name"), b.string("address"),
                b.integer("capacity"), b.integer("stockThreshold"), b.integer("freeSlotsThreshold"),
                b.object("shipping").map(Bodies::shipping), b.bool("affectsPrice"), b.bool("kitchen"))));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        branches.delete(id, Principals.userId(jwt));
        return ResponseEntity.noContent().build();
    }
}
