package com.fooda.api.finance.application.service;

import com.fooda.api.core.application.port.out.BranchRepository;
import com.fooda.api.core.application.port.out.DishRepository;
import com.fooda.api.core.application.port.out.PackRepository;
import com.fooda.api.core.application.port.out.SaleRepository;
import com.fooda.api.core.domain.catalog.Branch;
import com.fooda.api.core.domain.catalog.Dish;
import com.fooda.api.core.domain.catalog.Pack;
import com.fooda.api.core.domain.catalog.PackVersion;
import com.fooda.api.core.domain.catalog.PackVersions;
import com.fooda.api.core.domain.finance.Analysis;
import com.fooda.api.finance.application.port.in.AnalysisUseCase;
import com.fooda.api.finance.application.port.in.PackAnalysis;
import com.fooda.api.finance.application.port.in.VersionAt;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.shared.application.exception.ResourceNotFoundException;
import com.fooda.api.shared.domain.ValidationException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AnalysisService implements AnalysisUseCase {

    private static final String MISSING = "—";

    private final SaleRepository sales;
    private final PackRepository packs;
    private final DishRepository dishes;
    private final BranchRepository branches;
    private final BusinessCalendar calendar;

    public AnalysisService(SaleRepository sales, PackRepository packs, DishRepository dishes,
                           BranchRepository branches, BusinessCalendar calendar) {
        this.sales = sales;
        this.packs = packs;
        this.dishes = dishes;
        this.branches = branches;
        this.calendar = calendar;
    }

    @Override
    public PackAnalysis packs(LocalDate from, LocalDate to, String groupBy) {
        var g = Analysis.GroupBy.of(groupBy);
        LocalDate end = calendar.orToday(to);
        LocalDate start = from != null ? from : calendar.today().minusMonths(2).withDayOfMonth(1);
        List<Pack> allPacks = packs.findAll();
        Map<Long, String> packNames = allPacks.stream().collect(Collectors.toMap(Pack::id, Pack::name));
        Map<Long, String> branchNames = branches.findAll().stream()
                .collect(Collectors.toMap(Branch::id, Branch::name));
        var rows = Analysis.rows(sales.findAll(), start, end, g).stream()
                .map(r -> new PackAnalysis.Row(r.period(), r.packId(), packNames.getOrDefault(r.packId(), MISSING),
                        r.branchId(), branchNames.getOrDefault(r.branchId(), MISSING), r.quantity(), r.amount()))
                .toList();
        Map<Long, List<PackVersion>> versionsByPack = packs.findAllVersions().stream() // one query, not one per pack
                .sorted(PackVersions.BY_VALIDITY).collect(Collectors.groupingBy(PackVersion::packId));
        List<PackAnalysis.Change> changes = new ArrayList<>();
        for (Pack p : allPacks) {
            versionsByPack.getOrDefault(p.id(), List.of()).stream().skip(1)
                    .filter(v -> !v.validFrom().isBefore(start) && !v.validFrom().isAfter(end))
                    .forEach(v -> changes.add(new PackAnalysis.Change(p.id(), p.name(), v.validFrom(), v.id())));
        }
        return new PackAnalysis(start, end, g.code(), rows, changes);
    }

    @Override
    public VersionAt versionAt(long packId, LocalDate date) {
        packs.findById(packId).orElseThrow(() -> new ResourceNotFoundException("Pack no encontrado"));
        LocalDate day = calendar.orToday(date);
        List<PackVersion> versions = packs.findVersions(packId);
        int idx = -1;
        for (int i = 0; i < versions.size(); i++) {
            if (!versions.get(i).validFrom().isAfter(day)) {
                idx = i;
            }
        }
        if (idx < 0) {
            throw new ValidationException("date", "El pack no tenía versión en esa fecha");
        }
        PackVersion version = versions.get(idx);
        PackVersion previous = idx > 0 ? versions.get(idx - 1) : null;
        LocalDate until = idx + 1 < versions.size() ? versions.get(idx + 1).validFrom() : null;
        Map<Long, String> names = dishes.findAll().stream().collect(Collectors.toMap(Dish::id, Dish::name));
        Function<Long, String> name = id -> names.getOrDefault(id, MISSING);
        var menus = version.menus().stream().map(m -> new VersionAt.Menu(m.number(), name.apply(m.dishId())))
                .toList();
        var changes = previous == null ? List.<VersionAt.Change>of()
                : Analysis.menuChanges(previous, version).stream()
                .map(c -> new VersionAt.Change(c.number(), name.apply(c.beforeDishId()), name.apply(c.afterDishId()),
                        c.newNumber()))
                .toList();
        return new VersionAt(version, until, menus, previous, changes);
    }
}
