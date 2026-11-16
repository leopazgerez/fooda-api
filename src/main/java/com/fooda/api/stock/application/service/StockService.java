package com.fooda.api.stock.application.service;

import com.fooda.api.core.application.port.out.BranchRepository;
import com.fooda.api.core.application.port.out.PackRepository;
import com.fooda.api.core.application.port.out.ProductionRepository;
import com.fooda.api.core.application.port.out.SettingsRepository;
import com.fooda.api.core.application.port.out.StockMovementRepository;
import com.fooda.api.core.domain.catalog.Branch;
import com.fooda.api.core.domain.catalog.Pack;
import com.fooda.api.core.domain.catalog.Settings;
import com.fooda.api.core.domain.production.Production;
import com.fooda.api.core.domain.stock.StockRow;
import com.fooda.api.core.domain.stock.StockRules;
import com.fooda.api.stock.application.port.in.StockUseCase;
import com.fooda.api.stock.application.port.in.StockView;
import com.fooda.api.stock.application.port.in.StockView.BranchTotal;
import com.fooda.api.stock.application.port.in.StockView.Row;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class StockService implements StockUseCase {

    private static final String UNKNOWN = "—";

    private final StockMovementRepository movements;
    private final ProductionRepository productions;
    private final PackRepository packs;
    private final BranchRepository branches;
    private final SettingsRepository settings;

    public StockService(StockMovementRepository movements, ProductionRepository productions, PackRepository packs,
                        BranchRepository branches, SettingsRepository settings) {
        this.movements = movements;
        this.productions = productions;
        this.packs = packs;
        this.branches = branches;
        this.settings = settings;
    }

    @Override
    public StockView view(Long branchId, Long packId, Long productionId) {
        List<StockRow> current = StockRules.current(movements.findAll());
        List<Branch> allBranches = branches.findAll();
        Map<Long, Branch> branchById = byId(allBranches, Branch::id);
        Map<Long, Production> productionById = byId(productions.findAll(), Production::id);
        Map<Long, Pack> packById = byId(packs.findAll(), Pack::id);
        BigDecimal defaultPrice = settings.find().map(Settings::packPrice).orElse(null);

        List<Row> rows = current.stream()
                .filter(r -> branchId == null || r.branchId() == branchId)
                .filter(r -> packId == null || r.packId() == packId)
                .filter(r -> productionId == null || r.productionId() == productionId)
                .map(r -> {
                    Branch b = branchById.get(r.branchId());
                    Production p = productionById.get(r.productionId());
                    Pack k = packById.get(r.packId());
                    BigDecimal price = k != null && k.price() != null ? k.price() : defaultPrice;
                    return new Row(r.branchId(), b == null ? UNKNOWN : b.name(), r.productionId(),
                            p == null ? UNKNOWN : p.code(), p == null ? null : p.closedOn(), r.packId(),
                            k == null ? UNKNOWN : k.name(), r.quantity(), price);
                })
                .toList();

        Map<Long, Integer> totals = current.stream()
                .collect(Collectors.groupingBy(StockRow::branchId, Collectors.summingInt(StockRow::quantity)));
        List<BranchTotal> byBranch = allBranches.stream().map(b -> {
            int total = totals.getOrDefault(b.id(), 0);
            return new BranchTotal(b.id(), b.name(), total, b.capacity(), b.capacity() - total,
                    StockRules.alerts(b, total));
        }).toList();
        return new StockView(rows, byBranch);
    }

    private static <T> Map<Long, T> byId(List<T> list, Function<T, Long> id) {
        return list.stream().collect(Collectors.toMap(id, Function.identity()));
    }
}
