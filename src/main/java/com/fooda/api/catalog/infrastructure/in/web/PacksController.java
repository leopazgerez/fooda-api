package com.fooda.api.catalog.infrastructure.in.web;

import com.fooda.api.catalog.application.port.in.PackChanges;
import com.fooda.api.catalog.application.port.in.PackUseCase;
import com.fooda.api.catalog.application.port.in.PackView;
import com.fooda.api.core.domain.catalog.MenuSlot;
import com.fooda.api.core.domain.catalog.Pack;
import com.fooda.api.core.domain.catalog.PackVersion;
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
@RequestMapping("/api/packs")
public class PacksController {

    public record MenuResponse(int number, long dishId, String dish, BigDecimal cost) {
    }

    public record CurrentVersionResponse(long id, LocalDate validFrom, List<MenuResponse> menus) {
    }

    public record PackResponse(long id, int number, String name, boolean active, BigDecimal price,
                               BigDecimal salePrice, CurrentVersionResponse currentVersion, PackView.CostView cost,
                               BigDecimal profit, BigDecimal margin, Long version) {
        static PackResponse from(PackView v) {
            Pack p = v.pack();
            var cv = v.currentVersion();
            return new PackResponse(p.id(), p.number(), p.name(), p.active(), p.price(), v.salePrice(),
                    cv == null ? null : new CurrentVersionResponse(cv.version().id(), cv.version().validFrom(),
                            cv.menus().stream()
                                    .map(m -> new MenuResponse(m.number(), m.dishId(), m.dishName(), m.cost()))
                                    .toList()),
                    v.cost(), v.profit(), v.margin(), p.version());
        }
    }

    public record VersionResponse(long id, LocalDate validFrom, List<MenuSlot> menus) {
        static VersionResponse from(PackVersion v) {
            return new VersionResponse(v.id(), v.validFrom(), v.menus());
        }
    }

    private final PackUseCase packs;

    public PacksController(PackUseCase packs) {
        this.packs = packs;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CATALOG_READ')")
    public List<PackResponse> list() {
        return packs.list().stream().map(PackResponse::from).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CATALOG_READ')")
    public PackResponse get(@PathVariable long id) {
        return PackResponse.from(packs.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    public ResponseEntity<PackResponse> create(@RequestBody Map<String, Object> body) {
        var created = PackResponse.from(packs.create(new PatchBody(body).string("name").value()));
        return Bodies.created(created.id(), created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    public PackResponse update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        var b = new PatchBody(body);
        return PackResponse.from(packs.update(id, new PackChanges(b.string("name"), b.required(b.bool("active"), "active"),
                b.decimal("price"))));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        packs.delete(id, Principals.userId(jwt));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('CATALOG_READ')")
    public List<VersionResponse> versions(@PathVariable long id) {
        return packs.versions(id).stream().map(VersionResponse::from).toList();
    }

    @PostMapping("/{id}/versions")
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    public ResponseEntity<VersionResponse> addVersion(@PathVariable long id, @RequestBody Map<String, Object> body) {
        var b = new PatchBody(body);
        String from = b.string("validFrom").value();
        LocalDate validFrom;
        try {
            validFrom = from == null ? null : LocalDate.parse(from);
        } catch (DateTimeParseException e) {
            throw new ValidationException("validFrom", "Valor inválido");
        }
        List<Map<String, Object>> raw = Bodies.maps(b.list("menus").value(), "menus");
        if (raw == null) {
            throw new ValidationException("menus", "Un pack lleva exactamente 5 menús");
        }
        List<MenuSlot> menus = raw.stream().map(m -> {
            var mb = new PatchBody(m);
            Long dishId = mb.id("dishId").value();
            Integer number = mb.integer("number").value();
            if (dishId == null || number == null) {
                throw new ValidationException("menus", "Valor inválido");
            }
            return new MenuSlot(number, dishId);
        }).toList();
        var created = VersionResponse.from(packs.addVersion(id, validFrom, menus));
        return Bodies.created(created.id(), created);
    }
}
