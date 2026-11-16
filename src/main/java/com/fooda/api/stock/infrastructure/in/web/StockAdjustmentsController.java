package com.fooda.api.stock.infrastructure.in.web;

import com.fooda.api.core.domain.stock.StockMovement;
import com.fooda.api.shared.infrastructure.in.web.PatchBody;
import com.fooda.api.shared.infrastructure.in.web.Principals;
import com.fooda.api.shared.infrastructure.in.web.RequestBodies;
import com.fooda.api.stock.application.port.in.AdjustmentRequest;
import com.fooda.api.stock.application.port.in.AdjustmentUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/stock-adjustments")
public class StockAdjustmentsController {

    private final AdjustmentUseCase adjustments;

    public StockAdjustmentsController(AdjustmentUseCase adjustments) {
        this.adjustments = adjustments;
    }

    public record AdjustmentResponse(long id, LocalDate date, long branchId, long productionId, long packId,
                                     int quantity, String reason) {
    }

    @PostMapping
    @PreAuthorize("hasAuthority('STOCK_WRITE')")
    public ResponseEntity<AdjustmentResponse> create(@RequestBody Map<String, Object> body) {
        var b = new PatchBody(body);
        var m = adjustments.create(new AdjustmentRequest(RequestBodies.date(b, "date"), b.id("branchId").value(),
                b.id("packId").value(), b.id("productionId").value(), b.integer("quantity").value(),
                b.string("reason").value()));
        return RequestBodies.created(m.id(), response(m));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('STOCK_WRITE')")
    public ResponseEntity<Void> annul(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        adjustments.annul(id, Principals.userId(jwt));
        return ResponseEntity.noContent().build();
    }

    private static AdjustmentResponse response(StockMovement m) {
        return new AdjustmentResponse(m.id(), m.date(), m.branchId(), m.productionId(), m.packId(), m.quantity(),
                m.reason());
    }
}
