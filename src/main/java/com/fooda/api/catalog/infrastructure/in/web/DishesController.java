package com.fooda.api.catalog.infrastructure.in.web;

import com.fooda.api.catalog.application.port.in.DishChanges;
import com.fooda.api.catalog.application.port.in.DishUseCase;
import com.fooda.api.catalog.application.port.in.DishView;
import com.fooda.api.core.domain.catalog.Dish;
import com.fooda.api.core.domain.catalog.RecipeLine;
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
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dishes")
public class DishesController {

    public record RecipeLineResponse(long supplyId, BigDecimal quantity, String unit, BigDecimal portions) {
        static RecipeLineResponse from(RecipeLine l) {
            return new RecipeLineResponse(l.supplyId(), l.quantity(), l.unit().code(), l.portions());
        }
    }

    public record DishResponse(long id, String name, List<RecipeLineResponse> recipe, BigDecimal cost,
                               List<DishView.MenuRef> menus, Long version) {
        static DishResponse from(DishView v) {
            Dish d = v.dish();
            return new DishResponse(d.id(), d.name(), d.recipe().stream().map(RecipeLineResponse::from).toList(),
                    v.cost(), v.menus(), d.version());
        }
    }

    private final DishUseCase dishes;

    public DishesController(DishUseCase dishes) {
        this.dishes = dishes;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CATALOG_READ')")
    public Page<DishResponse> list(@RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "50") int size,
                                   @RequestParam(required = false) String sort,
                                   @RequestParam(required = false) String dir) {
        return dishes.list(new ListQuery(page, size, sort, dir)).map(DishResponse::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CATALOG_READ')")
    public DishResponse get(@PathVariable long id) {
        return DishResponse.from(dishes.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    public ResponseEntity<DishResponse> create(@RequestBody Map<String, Object> body) {
        var b = new PatchBody(body);
        var created = DishResponse.from(dishes.create(new Dish(null, b.string("name").value(),
                Bodies.recipe(b.list("recipe").value()), null)));
        return Bodies.created(created.id(), created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    public DishResponse update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        var b = new PatchBody(body);
        return DishResponse.from(dishes.update(id, new DishChanges(b.string("name"),
                b.list("recipe").map(Bodies::recipe))));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CATALOG_WRITE')")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        dishes.delete(id, Principals.userId(jwt));
        return ResponseEntity.noContent().build();
    }
}
