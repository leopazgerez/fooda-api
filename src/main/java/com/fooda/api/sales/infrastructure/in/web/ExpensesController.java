package com.fooda.api.sales.infrastructure.in.web;

import com.fooda.api.core.domain.expense.Expense;
import com.fooda.api.sales.application.port.in.ExpenseInput;
import com.fooda.api.sales.application.port.in.ExpenseUseCase;
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
import java.util.Map;

@RestController
@RequestMapping("/api/expenses")
public class ExpensesController {

    private final ExpenseUseCase expenses;

    public ExpensesController(ExpenseUseCase expenses) {
        this.expenses = expenses;
    }

    public record ExpenseResponse(Long id, LocalDate date, String type, String detail, BigDecimal amount,
                                  BigDecimal amountPacks, BigDecimal packPrice, String method, boolean paid,
                                  String notes, Long purchaseId, Long saleId, Long version) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SALES_READ')")
    public Page<ExpenseResponse> list(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size,
                                      @RequestParam(required = false) String sort,
                                      @RequestParam(required = false) String dir) {
        return expenses.list(new ListQuery(page, size, sort, dir)).map(ExpensesController::response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SALES_READ')")
    public ExpenseResponse get(@PathVariable long id) {
        return response(expenses.get(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SALES_WRITE')")
    public ResponseEntity<ExpenseResponse> create(@RequestBody Map<String, Object> body) {
        var r = response(expenses.create(input(new PatchBody(body))));
        return RequestBodies.created(r.id(), r);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('SALES_WRITE')")
    public ExpenseResponse update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return response(expenses.update(id, input(new PatchBody(body))));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('SALES_WRITE')")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        expenses.delete(id, Principals.userId(jwt));
        return ResponseEntity.noContent().build();
    }

    private static ExpenseInput input(PatchBody b) {
        return new ExpenseInput(RequestBodies.dateField(b, "date"), b.string("type"), b.string("detail"), b.money("amount"),
                b.decimal("amountPacks"), b.string("method"), b.bool("paid"), b.string("notes"),
                b.required(b.id("version"), "version"));
    }

    private static ExpenseResponse response(Expense e) {
        return new ExpenseResponse(e.id(), e.date(), e.type(), e.detail(), e.amount(), e.amountPacks(),
                e.packPrice(), e.method() == null ? null : e.method().code(), e.paid(), e.notes(), e.purchaseId(),
                e.saleId(), e.version());
    }
}
