package com.fooda.api.catalog.infrastructure.in.web;

import com.fooda.api.catalog.application.port.in.NewSupply;
import com.fooda.api.catalog.application.port.in.SupplyChanges;
import com.fooda.api.catalog.application.port.in.SupplyUseCase;
import com.fooda.api.catalog.application.port.in.SupplyView;
import com.fooda.api.core.domain.catalog.PackagingType;
import com.fooda.api.core.domain.catalog.Supply;
import com.fooda.api.core.domain.catalog.SupplyPrice;
import com.fooda.api.core.domain.catalog.Unit;
import com.fooda.api.shared.domain.ValidationException;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.Page;
import com.fooda.api.shared.infrastructure.in.web.PatchBody;
import com.fooda.api.shared.infrastructure.in.web.Principals;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/supplies")
public class SuppliesController {

    public record CompositeResponse(String rule, List<Long> supplyIds) {
    }

    public record SupplyResponse(long id, String name, String unit, long supplierId, String supplier,
                                 String presentation, BigDecimal content, BigDecimal gramsPerUnit,
                                 String packagingType, CompositeResponse composite, boolean active,
                                 BigDecimal unitCost, BigDecimal packagePrice, Long version) {
        static SupplyResponse from(SupplyView v) {
            Supply s = v.supply();
            return new SupplyResponse(s.id(), s.name(), s.unit().code(), s.supplierId(), v.supplierName(),
                    s.presentation(), s.content(), s.gramsPerUnit(),
                    s.packagingType() == null ? null : s.packagingType().code(),
                    s.composite() == null ? null
                            : new CompositeResponse(s.composite().rule().code(), s.composite().partIds()),
                    s.active(), v.unitCost(), v.packagePrice(), s.version());
        }
    }

    public record PriceResponse(long id, LocalDate date, BigDecimal packagePrice) {
        static PriceResponse from(SupplyPrice p) {
            return new PriceResponse(p.id(), p.date(), p.packagePrice());
        }
    }

    private final SupplyUseCase supplies;

    public SuppliesController(SupplyUseCase supplies) {
        this.supplies = supplies;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CATALOG_READ')")
    public Page<SupplyResponse> list(@RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size,
                                     @RequestParam(required = false) String sort,
                                     @RequestParam(required = false) String dir) {
        return supplies.list(new ListQuery(page, size, sort, dir)).map(SupplyResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CATALOG_READ')")
    public SupplyResponse get(@PathVariable long id) {
        return SupplyResponse.from(supplies.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    public ResponseEntity<SupplyResponse> create(@RequestBody Map<String, Object> body) {
        var b = new PatchBody(body);
        Long supplierId = b.id("supplierId").value();
        if (supplierId == null) {
            throw new ValidationException("supplierId", "Elegí un proveedor");
        }
        String unit = b.string("unit").value();
        String packaging = b.string("packagingType").value();
        var composite = b.object("composite").value();
        var created = SupplyResponse.from(supplies.create(new NewSupply(b.string("name").value(),
                unit == null ? null : Unit.of(unit, "unit"), supplierId, b.string("presentation").value(),
                b.decimal("content").value(), b.decimal("gramsPerUnit").value(),
                packaging == null ? null : PackagingType.of(packaging, "packagingType"),
                composite == null ? null : Bodies.composite(composite), b.decimal("packagePrice").value())));
        return Bodies.created(created.id(), created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    public SupplyResponse update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        var b = new PatchBody(body);
        return SupplyResponse.from(supplies.update(id, new SupplyChanges(b.string("name"),
                b.string("unit").map(v -> Unit.of(v, "unit")), b.required(b.id("supplierId"), "supplierId"), b.string("presentation"),
                b.decimal("content"), b.decimal("gramsPerUnit"),
                b.string("packagingType").map(v -> PackagingType.of(v, "packagingType")),
                b.object("composite").map(Bodies::composite), b.required(b.bool("active"), "active"))));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        supplies.delete(id, Principals.userId(jwt));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/prices")
    @PreAuthorize("hasAuthority('CATALOG_READ')")
    public List<PriceResponse> prices(@PathVariable long id) {
        return supplies.prices(id).stream().map(PriceResponse::from).toList();
    }

    @PostMapping("/{id}/prices")
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    public ResponseEntity<PriceResponse> addPrice(@PathVariable long id, @RequestBody Map<String, Object> body) {
        var b = new PatchBody(body);
        String date = b.string("date").value();
        LocalDate d;
        try {
            d = date == null ? null : LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new ValidationException("date", "Valor inválido");
        }
        var created = PriceResponse.from(supplies.addPrice(id, b.decimal("packagePrice").value(), d));
        return Bodies.created(created.id(), created);
    }

    @DeleteMapping("/{id}/prices/{priceId}")
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    public ResponseEntity<Void> deletePrice(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                            @PathVariable long priceId) {
        supplies.deletePrice(id, priceId, Principals.userId(jwt));
        return ResponseEntity.noContent().build();
    }
}
