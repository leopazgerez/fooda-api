package com.fooda.api.sales.infrastructure.in.web;

import com.fooda.api.core.domain.sales.Payment;
import com.fooda.api.sales.application.port.in.SaleFilter;
import com.fooda.api.sales.application.port.in.SaleRequest;
import com.fooda.api.sales.application.port.in.SaleUseCase;
import com.fooda.api.sales.application.port.in.SaleView;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.Page;
import com.fooda.api.shared.infrastructure.in.web.PatchBody;
import com.fooda.api.shared.infrastructure.in.web.Principals;
import com.fooda.api.shared.infrastructure.in.web.RequestBodies;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sales")
public class SalesController {

    private final SaleUseCase sales;

    public SalesController(SaleUseCase sales) {
        this.sales = sales;
    }

    public record LineResponse(long productionId, String lot, long packId, String pack, int quantity,
                               BigDecimal unitPrice) {
    }

    public record PaymentResponse(Long id, LocalDate date, BigDecimal amount, String method) {
    }

    public record SaleResponse(long id, LocalDate date, long branchId, String branch, Long customerId,
                               String customer, String type, String reason, String method, BigDecimal discount,
                               BigDecimal shipping, String notes, int packs, List<LineResponse> lines,
                               BigDecimal total, BigDecimal profit, List<PaymentResponse> payments) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SALES_READ')")
    public Page<SaleResponse> list(@RequestParam(required = false) Long branchId,
                                   @RequestParam(required = false) String type,
                                   @RequestParam(required = false) Long customerId,
                                   @RequestParam(required = false) LocalDate from,
                                   @RequestParam(required = false) LocalDate to,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size,
                                   @RequestParam(required = false) String sort,
                                   @RequestParam(required = false) String dir) {
        return sales.list(new SaleFilter(branchId, type, customerId, from, to), new ListQuery(page, size, sort, dir))
                .map(SalesController::response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SALES_READ')")
    public SaleResponse get(@PathVariable long id) {
        return response(sales.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SALES_WRITE')")
    public ResponseEntity<SaleResponse> create(@RequestBody Map<String, Object> body) {
        var b = new PatchBody(body);
        var lines = RequestBodies.optionalItems(b, "lines").stream().map(l -> new SaleRequest.LineRequest(
                l.id("productionId").value(), l.id("packId").value(), l.integer("quantity").value(),
                l.money("unitPrice").value())).toList();
        var r = response(sales.create(new SaleRequest(RequestBodies.date(b, "date"), b.id("branchId").value(),
                b.id("customerId").value(), b.string("type").value(), b.string("method").value(),
                b.string("reason").value(), b.money("discount").value(), b.money("shipping").value(),
                b.string("notes").value(), b.string("expenseType").value(), lines)));
        return RequestBodies.created(r.id(), r);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SALES_WRITE')")
    public ResponseEntity<Void> annul(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        sales.annul(id, Principals.userId(jwt));
        return ResponseEntity.noContent().build();
    }

    private static SaleResponse response(SaleView v) {
        var s = v.sale();
        return new SaleResponse(s.id(), s.date(), s.branchId(), v.branch(), s.customerId(), v.customer(),
                s.type().code(), s.reason(), s.method().code(), s.discount(), s.shipping(), s.notes(), v.packs(),
                v.lines().stream().map(l -> new LineResponse(l.productionId(), l.lot(), l.packId(), l.pack(),
                        l.quantity(), l.unitPrice())).toList(), v.total(), v.profit(),
                v.payments().stream().map(p -> new PaymentResponse(p.id(), p.date(), p.amount(), p.method().code()))
                        .toList());
    }
}
