package com.fooda.api.sales.infrastructure.in.web;

import com.fooda.api.sales.application.port.in.CustomerInput;
import com.fooda.api.sales.application.port.in.CustomerUseCase;
import com.fooda.api.sales.application.port.in.CustomerView;
import com.fooda.api.sales.application.port.in.FollowUpsView;
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
@RequestMapping("/api/customers")
public class CustomersController {

    private final CustomerUseCase customers;

    public CustomersController(CustomerUseCase customers) {
        this.customers = customers;
    }

    public record CustomerResponse(Long id, String firstName, String lastName, String phone, String address,
                                   String birthday, String notes, Long version, String fullName, boolean regular,
                                   LocalDate lastPurchase, int packs, BigDecimal total) {
    }

    public record FollowUpResponse(CustomerResponse customer, LocalDate lastPurchase, long daysWithoutPurchase,
                                   List<Long> packIds, List<String> packs) {
    }

    public record FollowUpsResponse(List<FollowUpResponse> followUps, List<CustomerResponse> birthdays) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SALES_READ')")
    public Page<CustomerResponse> list(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       @RequestParam(required = false) String sort,
                                       @RequestParam(required = false) String dir) {
        return customers.list(new ListQuery(page, size, sort, dir)).map(CustomersController::response);
    }

    // literal path: Spring prefers it over /{id}
    @GetMapping("/follow-ups")
    @PreAuthorize("hasAuthority('SALES_READ')")
    public FollowUpsResponse followUps() {
        FollowUpsView v = customers.followUps();
        return new FollowUpsResponse(v.followUps().stream().map(f -> new FollowUpResponse(response(f.customer()),
                f.lastPurchase(), f.daysWithoutPurchase(), f.packIds(), f.packs())).toList(),
                v.birthdays().stream().map(CustomersController::response).toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SALES_READ')")
    public CustomerResponse get(@PathVariable long id) {
        return response(customers.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SALES_WRITE')")
    public ResponseEntity<CustomerResponse> create(@RequestBody Map<String, Object> body) {
        var r = response(customers.create(input(new PatchBody(body))));
        return RequestBodies.created(r.id(), r);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('SALES_WRITE')")
    public CustomerResponse update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return response(customers.update(id, input(new PatchBody(body))));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SALES_WRITE')")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        customers.delete(id, Principals.userId(jwt));
        return ResponseEntity.noContent().build();
    }

    private static CustomerInput input(PatchBody b) {
        return new CustomerInput(b.string("firstName"), b.string("lastName"), b.string("phone"),
                b.string("address"), b.string("birthday"), b.string("notes"),
                b.required(b.id("version"), "version"));
    }

    private static CustomerResponse response(CustomerView v) {
        var c = v.customer();
        return new CustomerResponse(c.id(), c.firstName(), c.lastName(), c.phone(), c.address(), c.birthday(),
                c.notes(), c.version(), v.fullName(), v.regular(), v.lastPurchase(), v.packs(), v.total());
    }
}
