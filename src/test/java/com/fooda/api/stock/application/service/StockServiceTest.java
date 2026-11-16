package com.fooda.api.stock.application.service;

import com.fooda.api.catalog.fakes.InMemoryBranchRepository;
import com.fooda.api.catalog.fakes.InMemoryPackRepository;
import com.fooda.api.catalog.fakes.InMemorySettingsRepository;
import com.fooda.api.core.domain.catalog.*;
import com.fooda.api.core.domain.production.*;
import com.fooda.api.core.domain.stock.BranchAlert;
import com.fooda.api.core.domain.stock.MovementType;
import com.fooda.api.core.domain.stock.StockMovement;
import com.fooda.api.core.domain.stock.StockRules;
import com.fooda.api.production.fakes.InMemoryProductionRepository;
import com.fooda.api.production.fakes.InMemoryStockMovementRepository;
import com.fooda.api.stock.application.port.in.StockUseCase;
import com.fooda.api.stock.application.port.in.StockView;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StockServiceTest {

    private final InMemoryStockMovementRepository movements = new InMemoryStockMovementRepository();
    private final InMemoryProductionRepository productions = new InMemoryProductionRepository();
    private final InMemoryPackRepository packs = new InMemoryPackRepository();
    private final InMemoryBranchRepository branches = new InMemoryBranchRepository();
    private final InMemorySettingsRepository settings = new InMemorySettingsRepository();
    private final StockUseCase service = new StockService(movements, productions, packs, branches, settings);

    @Test
    void viewFiltersRowsButTotalsAllStock() {
        Settings s = new Settings(new BigDecimal("45000"), new BigDecimal("20"), 4L, 1, 1,
                new BigDecimal("1000"), 3, 30, BigDecimal.TEN, List.of(), List.of(Settings.FIXED_EXPENSE_TYPE), null);
        settings.save(s);
        long classic = packs.save(new Pack(null, 1, "Clásico", true, new BigDecimal("50000"), null)).id();
        long green = packs.save(new Pack(null, 2, "Verde", true, null, null)).id();
        Branch salta = branches.save(new Branch(null, "Salta", "", 10, 1, 1, new Shipping(ShippingType.AMOUNT,
                BigDecimal.ZERO), false, true, null));
        Branch jvg = branches.save(new Branch(null, "JVG", "", 10, 1, 1, new Shipping(ShippingType.AMOUNT,
                BigDecimal.ZERO), false, false, null));
        LocalDate d = LocalDate.of(2026, 11, 3);
        long lot = productions.save(new Production(null, "L1", 1, ProductionStatus.CLOSED, d, d, List.of(),
                ProductionSnapshot.empty(s), null, null, null)).id();
        movements.add(new StockMovement(null, d, salta.id(), lot, classic, 6, MovementType.PRODUCTION, lot, null));
        movements.add(new StockMovement(null, d, salta.id(), lot, green, 4, MovementType.PRODUCTION, lot, null));
        movements.add(new StockMovement(null, d, jvg.id(), lot, classic, 4, MovementType.TRANSFER_IN, 9L, null));
        movements.add(new StockMovement(null, d, salta.id(), lot, classic, -4, MovementType.TRANSFER_OUT, 9L, null));

        StockView all = service.view(null, null, null);
        assertThat(all.rows()).extracting(r -> List.of(r.branchId(), r.packId(), (long) r.quantity()))
                .containsExactly(List.of(salta.id(), classic, 2L), List.of(salta.id(), green, 4L),
                        List.of(jvg.id(), classic, 4L));
        assertThat(all.rows()).extracting(StockView.Row::lot).containsOnly("L1");
        assertThat(all.rows()).extracting(StockView.Row::lotDate).containsOnly(d);
        assertThat(all.rows()).extracting(StockView.Row::price)
                .containsExactly(new BigDecimal("50000"), new BigDecimal("45000"), new BigDecimal("50000"));
        assertThat(all.rows().get(0).branch()).isEqualTo("Salta");
        assertThat(all.rows().get(1).pack()).isEqualTo("Verde");

        StockView onlyJvg = service.view(jvg.id(), null, null);
        assertThat(onlyJvg.rows()).hasSize(1);
        assertThat(onlyJvg.rows().get(0).branchId()).isEqualTo(jvg.id());
        assertThat(onlyJvg.byBranch()).extracting(StockView.BranchTotal::total).containsExactly(6, 4);
        assertThat(onlyJvg.byBranch()).extracting(StockView.BranchTotal::freeSlots).containsExactly(4, 6);
        assertThat(onlyJvg.byBranch().get(0).alerts()).isEqualTo(StockRules.alerts(salta, 6));
        assertThat(onlyJvg.byBranch().get(1).alerts()).isEqualTo(StockRules.alerts(jvg, 4));
        assertThat(onlyJvg.byBranch().get(1).alerts()).containsExactly(BranchAlert.FREE_SLOTS);
        assertThat(service.view(null, green, null).rows()).hasSize(1);
        assertThat(service.view(null, null, lot + 99).rows()).isEmpty();
    }
}
