package com.fooda.api.stock.infrastructure.in.web;

import com.fooda.api.core.domain.stock.OutLine;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.Page;
import com.fooda.api.shared.infrastructure.in.web.PatchBody;
import com.fooda.api.shared.infrastructure.in.web.Principals;
import com.fooda.api.shared.infrastructure.in.web.RequestBodies;
import com.fooda.api.stock.application.port.in.TransferRequest;
import com.fooda.api.stock.application.port.in.TransferUseCase;
import com.fooda.api.stock.application.port.in.TransferView;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/transfers")
public class TransfersController {

    private final TransferUseCase transfers;

    public TransfersController(TransferUseCase transfers) {
        this.transfers = transfers;
    }

    public record LineResponse(long productionId, String lot, long packId, String pack, int quantity) {
    }

    public record TransferResponse(long id, LocalDate date, long originBranchId, String origin,
                                   long destinationBranchId, String destination, String notes,
                                   Long sourceProductionId, int packs, List<LineResponse> lines) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('STOCK_READ')")
    public Page<TransferResponse> list(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       @RequestParam(required = false) String sort,
                                       @RequestParam(required = false) String dir) {
        return transfers.list(new ListQuery(page, size, sort, dir)).map(TransfersController::response);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('STOCK_WRITE')")
    public ResponseEntity<TransferResponse> create(@RequestBody Map<String, Object> body) {
        var b = new PatchBody(body);
        var lines = RequestBodies.optionalItems(b, "lines").stream().map(l -> new OutLine(
                RequestBodies.id(l, "productionId"), RequestBodies.id(l, "packId"),
                RequestBodies.integer(l, "quantity"))).toList();
        var r = response(transfers.create(new TransferRequest(RequestBodies.date(b, "date"),
                b.id("originBranchId").value(), b.id("destinationBranchId").value(), b.string("notes").value(),
                lines)));
        return RequestBodies.created(r.id(), r);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('STOCK_WRITE')")
    public ResponseEntity<Void> annul(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        transfers.annul(id, Principals.userId(jwt));
        return ResponseEntity.noContent().build();
    }

    private static TransferResponse response(TransferView v) {
        var t = v.transfer();
        return new TransferResponse(t.id(), t.date(), t.originBranchId(), v.origin(), t.destinationBranchId(),
                v.destination(), t.notes(), t.sourceProductionId(), v.packs(), v.lines().stream().map(l ->
                new LineResponse(l.productionId(), l.lot(), l.packId(), l.pack(), l.quantity())).toList());
    }
}
