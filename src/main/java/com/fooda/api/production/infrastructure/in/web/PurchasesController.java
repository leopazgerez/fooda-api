package com.fooda.api.production.infrastructure.in.web;

import com.fooda.api.production.application.port.in.BoughtPurchase;
import com.fooda.api.production.application.port.in.PriceAlert;
import com.fooda.api.production.application.port.in.PurchaseUseCase;
import com.fooda.api.production.application.port.in.PurchaseView;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/purchases")
public class PurchasesController {

    private final PurchaseUseCase purchases;

    public PurchasesController(PurchaseUseCase purchases) {
        this.purchases = purchases;
    }

    public record LineResponse(long supplyId, String supply, String unit, BigDecimal content, BigDecimal quantity,
                               BigDecimal packagePrice) {
    }

    public record PurchaseResponse(long id, Long productionId, String production, long supplierId, String supplier,
                                   String status, LocalDate date, BigDecimal total, Long version,
                                   List<LineResponse> lines) {
    }

    public record BoughtResponse(PurchaseResponse purchase, List<PriceAlert> alerts) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCTION_READ')")
    public Page<PurchaseResponse> list(@RequestParam(required = false) Long productionId,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       @RequestParam(required = false) String sort,
                                       @RequestParam(required = false) String dir) {
        return purchases.list(new ListQuery(page, size, sort, dir), productionId).map(PurchasesController::response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCTION_READ')")
    public PurchaseResponse get(@PathVariable long id) {
        return response(purchases.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCTION_WRITE')")
    public ResponseEntity<BoughtResponse> buyLoose(@RequestBody Map<String, Object> body) {
        var b = new PatchBody(body);
        var r = response(purchases.buyLoose(Web.id(b, "supplierId"), Web.date(b, "date"), Web.purchaseLines(b)));
        return Web.created(r.purchase().id(), r);
    }

    @PostMapping("/{id}/buy")
    @PreAuthorize("hasAuthority('PRODUCTION_WRITE')")
    public BoughtResponse buy(@PathVariable long id, @RequestBody Map<String, Object> body) {
        var b = new PatchBody(body);
        return response(purchases.buy(id, Web.date(b, "date"), Web.purchaseLines(b)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCTION_WRITE')")
    public ResponseEntity<Void> annul(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        purchases.annul(id, Principals.userId(jwt));
        return ResponseEntity.noContent().build();
    }

    private static BoughtResponse response(BoughtPurchase b) {
        return new BoughtResponse(response(b.purchase()), b.alerts());
    }

    private static PurchaseResponse response(PurchaseView v) {
        var p = v.purchase();
        return new PurchaseResponse(p.id(), p.productionId(), v.production(), p.supplierId(), v.supplier(),
                p.status().code(), p.date(), v.total(), p.version(), v.lines().stream().map(l ->
                new LineResponse(l.supplyId(), l.supply(), l.unit() == null ? null : l.unit().code(), l.content(), l.quantity(),
                        l.packagePrice())).toList());
    }
}
