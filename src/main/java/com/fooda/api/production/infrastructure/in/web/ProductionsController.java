package com.fooda.api.production.infrastructure.in.web;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fooda.api.core.domain.finance.AppliedOverhead;
import com.fooda.api.core.domain.finance.Payback;
import com.fooda.api.core.domain.production.ShoppingItem;
import com.fooda.api.core.domain.stock.Transfer;
import com.fooda.api.production.application.port.in.CloseRequest;
import com.fooda.api.production.application.port.in.ProductionSummary;
import com.fooda.api.production.application.port.in.ProductionUseCase;
import com.fooda.api.production.application.port.in.RecipeBookPack;
import com.fooda.api.production.application.port.in.ShoppingListGroup;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.Page;
import com.fooda.api.shared.domain.ValidationException;
import com.fooda.api.shared.infrastructure.in.web.PatchBody;
import com.fooda.api.shared.infrastructure.in.web.RequestBodies;
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
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/productions")
public class ProductionsController {

    private final ProductionUseCase productions;

    public ProductionsController(ProductionUseCase productions) {
        this.productions = productions;
    }

    public record PackLineResponse(long packId, String name, int planned, Integer actual, Long versionId,
                                   BigDecimal directEstimated, BigDecimal directActual, BigDecimal costPerPack) {
    }

    public record ProductionResponse(long id, String code, int number, String status, LocalDate start,
                                     LocalDate closedOn, int packs, BigDecimal estimatedCost, BigDecimal actualCost,
                                     int sold, BigDecimal soldRatio, AppliedOverhead overhead,
                                     List<PackLineResponse> packLines, Payback payback, Long version) {
    }

    public record DetailResponse(@JsonUnwrapped ProductionResponse summary, Map<Long, BigDecimal> leftovers,
                                 List<Transfer> transfers) {
    }

    public record ShoppingItemResponse(long supplyId, String name, BigDecimal quantity, String unit,
                                       BigDecimal unitCost, BigDecimal total) {
    }

    public record ShoppingGroupResponse(long supplierId, String supplier, List<ShoppingItemResponse> items,
                                        BigDecimal total, BigDecimal totalInPacks, Long purchaseId, String status) {
    }

    public record IngredientResponse(String name, BigDecimal quantity, String unit) {
    }

    public record MenuResponse(int number, String dish, List<IngredientResponse> ingredients,
                               RecipeBookPackShared sharedWith, Integer combinedTotal) {
    }

    public record RecipeBookPackShared(long packId, String name, int quantity) {
    }

    public record RecipeBookResponse(long packId, String name, int quantity, List<MenuResponse> menus) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCTION_READ')")
    public Page<ProductionResponse> list(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size,
                                         @RequestParam(required = false) String sort,
                                         @RequestParam(required = false) String dir) {
        return productions.list(new ListQuery(page, size, sort, dir)).map(ProductionsController::response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCTION_READ')")
    public DetailResponse get(@PathVariable long id) {
        var d = productions.get(id);
        return new DetailResponse(response(d.summary()), d.leftovers(), d.transfers());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCTION_WRITE')")
    public ResponseEntity<ProductionResponse> plan(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestBody Map<String, Object> body) {
        var b = new PatchBody(body);
        var lines = RequestBodies.items(b, "lines").stream()
                .map(l -> new ProductionUseCase.PlanLine(RequestBodies.id(l, "packId"), RequestBodies.integer(l, "planned"))).toList();
        var r = response(productions.plan(lines, RequestBodies.date(b, "start"), Principals.userId(jwt)));
        return RequestBodies.created(r.id(), r);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCTION_WRITE')")
    public ResponseEntity<Void> annul(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        productions.annul(id, Principals.userId(jwt));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/shopping-list")
    @PreAuthorize("hasAuthority('PRODUCTION_READ')")
    public List<ShoppingGroupResponse> shoppingList(@PathVariable long id) {
        return productions.shoppingList(id).stream().map(ProductionsController::response).toList();
    }

    @GetMapping("/{id}/recipe-book")
    @PreAuthorize("hasAuthority('PRODUCTION_READ')")
    public List<RecipeBookResponse> recipeBook(@PathVariable long id) {
        return productions.recipeBook(id).stream().map(ProductionsController::response).toList();
    }

    @PatchMapping("/{id}/leftovers")
    @PreAuthorize("hasAuthority('PRODUCTION_WRITE')")
    public ProductionResponse leftovers(@AuthenticationPrincipal Jwt jwt, @PathVariable long id,
                                        @RequestBody Map<String, Object> body) {
        var b = new PatchBody(body);
        var raw = b.need(b.object("leftovers"), "leftovers");
        var leftovers = new LinkedHashMap<Long, BigDecimal>();
        raw.forEach((k, v) -> {
            try {
                if (!(v instanceof Number n)) {
                    throw new ValidationException("leftovers", "Valor inválido");
                }
                leftovers.put(Long.parseLong(k), new BigDecimal(n.toString()));
            } catch (NumberFormatException e) {
                throw new ValidationException("leftovers", "Valor inválido");
            }
        });
        Long version = b.required(b.id("version"), "version").value();
        return response(productions.setLeftovers(id, leftovers, version, Principals.userId(jwt)));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('PRODUCTION_WRITE')")
    public ProductionResponse close(@PathVariable long id,
                                    @RequestBody(required = false) Map<String, Object> body) {
        var b = new PatchBody(body);
        var lines = RequestBodies.optionalItems(b, "lines").stream()
                .map(l -> new CloseRequest.Actual(RequestBodies.id(l, "packId"), RequestBodies.integer(l, "actual"))).toList();
        var distribution = RequestBodies.optionalItems(b, "distribution").stream()
                .map(d -> new CloseRequest.Distribution(RequestBodies.id(d, "branchId"), RequestBodies.id(d, "packId"),
                        RequestBodies.integer(d, "quantity"))).toList();
        return response(productions.close(id, new CloseRequest(RequestBodies.date(b, "date"), lines, distribution)));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAuthority('PRODUCTION_WRITE')")
    public ProductionResponse reopen(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        return response(productions.reopen(id, Principals.userId(jwt)));
    }

    private static ProductionResponse response(ProductionSummary s) {
        return new ProductionResponse(s.id(), s.code(), s.number(), s.status().code(), s.start(), s.closedOn(),
                s.packs(), s.estimatedCost(), s.actualCost(), s.sold(), s.soldRatio(), s.overhead(),
                s.packLines().stream().map(l -> new PackLineResponse(l.packId(), l.name(), l.planned(), l.actual(),
                        l.versionId(), l.directEstimated(), l.directActual(), l.costPerPack())).toList(),
                s.payback(), s.version());
    }

    private static ShoppingGroupResponse response(ShoppingListGroup g) {
        return new ShoppingGroupResponse(g.supplierId(), g.supplier(), g.items().stream().map(ProductionsController::item)
                .toList(), g.total(), g.totalInPacks(), g.purchaseId(), g.status().code());
    }

    private static ShoppingItemResponse item(ShoppingItem i) {
        return new ShoppingItemResponse(i.supplyId(), i.name(), i.quantity(), i.unit().code(), i.unitCost(), i.total());
    }

    private static RecipeBookResponse response(RecipeBookPack p) {
        return new RecipeBookResponse(p.packId(), p.name(), p.quantity(), p.menus().stream().map(m ->
                new MenuResponse(m.number(), m.dish(), m.ingredients().stream().map(i ->
                        new IngredientResponse(i.name(), i.quantity(), i.unit().code())).toList(),
                        m.sharedWith() == null ? null : new RecipeBookPackShared(m.sharedWith().packId(),
                                m.sharedWith().name(), m.sharedWith().quantity()), m.combinedTotal())).toList());
    }
}
