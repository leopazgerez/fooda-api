package com.fooda.api.stock.infrastructure.in.web;

import com.fooda.api.core.domain.stock.BranchAlert;
import com.fooda.api.stock.application.port.in.StockUseCase;
import com.fooda.api.stock.application.port.in.StockView;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockUseCase stock;

    public StockController(StockUseCase stock) {
        this.stock = stock;
    }

    public record BranchTotalResponse(long branchId, String branch, int total, int capacity, int freeSlots,
                                      List<String> alerts) {
    }

    public record StockResponse(List<StockView.Row> rows, List<BranchTotalResponse> byBranch) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('STOCK_READ')")
    public StockResponse view(@RequestParam(required = false) Long branchId,
                              @RequestParam(required = false) Long packId,
                              @RequestParam(required = false) Long productionId) {
        var v = stock.view(branchId, packId, productionId);
        return new StockResponse(v.rows(), v.byBranch().stream().map(b -> new BranchTotalResponse(b.branchId(),
                b.branch(), b.total(), b.capacity(), b.freeSlots(),
                b.alerts().stream().map(BranchAlert::code).toList())).toList());
    }
}
