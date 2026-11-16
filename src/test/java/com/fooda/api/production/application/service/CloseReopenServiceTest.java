package com.fooda.api.production.application.service;

import com.fooda.api.catalog.fakes.*;
import com.fooda.api.core.domain.catalog.*;
import com.fooda.api.core.domain.finance.MonthClose;
import com.fooda.api.core.domain.production.*;
import com.fooda.api.core.domain.stock.MovementType;
import com.fooda.api.core.domain.stock.StockMovement;
import com.fooda.api.core.domain.stock.Transfer;
import com.fooda.api.production.application.port.in.CloseRequest;
import com.fooda.api.production.application.port.in.CloseRequest.Actual;
import com.fooda.api.production.application.port.in.CloseRequest.Distribution;
import com.fooda.api.production.application.port.in.ProductionSummary;
import com.fooda.api.production.application.port.in.ProductionUseCase;
import com.fooda.api.production.fakes.*;
import com.fooda.api.shared.DirectTransactions;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.shared.application.exception.ResourceNotFoundException;
import com.fooda.api.shared.domain.ConflictException;
import com.fooda.api.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloseReopenServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);
    private static final long ACTOR = 1L;

    private final InMemorySupplyRepository supplyRepo = new InMemorySupplyRepository();
    private final InMemorySupplierRepository supplierRepo = new InMemorySupplierRepository();
    private final InMemoryPackRepository packRepo = new InMemoryPackRepository();
    private final InMemorySettingsRepository settingsRepo = new InMemorySettingsRepository();
    private final InMemoryBranchRepository branchRepo = new InMemoryBranchRepository();
    private final InMemoryProductionRepository productionRepo = new InMemoryProductionRepository();
    private final InMemoryPurchaseRepository purchaseRepo = new InMemoryPurchaseRepository();
    private final InMemoryTransferRepository transferRepo = new InMemoryTransferRepository();
    private final InMemoryStockMovementRepository movementRepo = new InMemoryStockMovementRepository();
    private final NoopStockLock lock = new NoopStockLock();
    private final FixedMonthCloses closes = new FixedMonthCloses();
    private final ProductionUseCase service = new ProductionService(productionRepo, purchaseRepo, packRepo,
            new InMemoryDishRepository(), supplyRepo, supplierRepo, settingsRepo, new InMemoryExpenseRepository(),
            closes, new FixedSoldPacks(), transferRepo, movementRepo, branchRepo, lock,
            new PurchasePlanner(purchaseRepo, supplyRepo, supplierRepo),
            new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), ZoneOffset.UTC)),
            new DirectTransactions());

    private final long classic = packRepo.save(new Pack(null, 1, "Clásico", true, null, null)).id();
    private final long green = packRepo.save(new Pack(null, 2, "Verde", true, null, null)).id();
    private final long other = packRepo.save(new Pack(null, 3, "Otro", true, null, null)).id();
    private final long salta;
    private final long jvg;
    private final long id;

    {
        settingsRepo.save(new Settings(new BigDecimal("45000"), new BigDecimal("20"), 4L, 1, 1,
                new BigDecimal("1000"), 3, 30, BigDecimal.TEN, List.of(), List.of(Settings.FIXED_EXPENSE_TYPE), null));
        salta = branchRepo.save(branch("Salta", true)).id();
        jvg = branchRepo.save(branch("JVG", false)).id();
        id = production(1).id();
    }

    private static Branch branch(String name, boolean kitchen) {
        return new Branch(null, name, "", 10, 1, 1, new Shipping(ShippingType.AMOUNT, BigDecimal.ZERO), false, kitchen,
                null);
    }

    private Production production(int number) {
        LocalDate d = LocalDate.of(2026, 11, 3);
        return productionRepo.save(new Production(null, ProductionRules.lotCode(number, d), number,
                ProductionStatus.PLANNED, d, null,
                List.of(new ProductionLine(classic, 5, null), new ProductionLine(green, 5, null)),
                ProductionSnapshot.empty(settingsRepo.find().orElseThrow()), null, null, null));
    }

    private ProductionSummary closeWithTwoToJvg() {
        return service.close(id, new CloseRequest(null, List.of(), List.of(new Distribution(jvg, classic, 2))));
    }

    private static void assertInvalid(Runnable call, String field, String message) {
        assertThatThrownBy(call::run).isInstanceOf(ValidationException.class).hasMessage(message)
                .extracting("field").isEqualTo(field);
    }

    private static void assertConflict(Runnable call, String field, String message) {
        assertThatThrownBy(call::run).isInstanceOf(ConflictException.class).hasMessage(message)
                .extracting("field").isEqualTo(field);
    }

    private void assertStillPlanned() {
        Production p = productionRepo.findById(id).orElseThrow();
        assertThat(p.status()).isEqualTo(ProductionStatus.PLANNED);
        assertThat(p.closedOn()).isNull();
        assertThat(p.lines()).allSatisfy(l -> assertThat(l.actual()).isNull());
        assertThat(movementRepo.all()).isEmpty();
        assertThat(transferRepo.findBySourceProduction(id)).isEmpty();
    }

    @Test
    void closeAddsKitchenStockAndDistributionTransfers() {
        ProductionSummary s = service.close(id, new CloseRequest(null, List.of(new Actual(classic, 5)),
                List.of(new Distribution(jvg, classic, 2), new Distribution(jvg, green, 0))));

        assertThat(s.status()).isEqualTo(ProductionStatus.CLOSED);
        assertThat(s.closedOn()).isEqualTo(TODAY);
        Production p = productionRepo.findById(id).orElseThrow();
        assertThat(p.lines()).extracting(ProductionLine::actual).containsExactly(5, 5);

        Transfer t = transferRepo.findBySourceProduction(id).get(0);
        assertThat(transferRepo.findBySourceProduction(id)).hasSize(1);
        assertThat(t.originBranchId()).isEqualTo(salta);
        assertThat(t.destinationBranchId()).isEqualTo(jvg);
        assertThat(t.notes()).isEqualTo("Reparto al cerrar 1 NOV 26");
        assertThat(t.date()).isEqualTo(TODAY);
        assertThat(t.lines()).singleElement().satisfies(l -> {
            assertThat(l.productionId()).isEqualTo(id);
            assertThat(l.packId()).isEqualTo(classic);
            assertThat(l.quantity()).isEqualTo(2);
        });

        assertThat(movementRepo.all()).extracting(m -> List.of(m.branchId(), m.packId(), m.quantity(), m.type(),
                        m.refId()))
                .containsExactly(
                        List.of(salta, classic, 5, MovementType.PRODUCTION, id),
                        List.of(salta, green, 5, MovementType.PRODUCTION, id),
                        List.of(salta, classic, -2, MovementType.TRANSFER_OUT, t.id()),
                        List.of(jvg, classic, 2, MovementType.TRANSFER_IN, t.id()));
        assertThat(movementRepo.all()).allSatisfy(m -> {
            assertThat(m.productionId()).isEqualTo(id);
            assertThat(m.date()).isEqualTo(TODAY);
        });
    }

    @Test
    void distributionToTwoBranchesMakesOneTransferEach() {
        long centro = branchRepo.save(branch("Centro", false)).id();

        service.close(id, new CloseRequest(null, List.of(), List.of(new Distribution(jvg, classic, 2),
                new Distribution(centro, classic, 1), new Distribution(jvg, green, 3))));

        List<Transfer> ts = transferRepo.findBySourceProduction(id);
        assertThat(ts).extracting(Transfer::destinationBranchId).containsExactly(jvg, centro);
        assertThat(ts.get(0).lines()).extracting(l -> List.of(l.packId(), l.quantity()))
                .containsExactly(List.of(classic, 2), List.of(green, 3));
        assertThat(ts.get(1).lines()).extracting(l -> List.of(l.packId(), l.quantity()))
                .containsExactly(List.of(classic, 1));
        assertThat(movementRepo.all()).extracting(m -> List.of(m.branchId(), m.packId(), m.quantity(), m.refId()))
                .containsExactly(
                        List.of(salta, classic, 5, id), List.of(salta, green, 5, id),
                        List.of(salta, classic, -2, ts.get(0).id()), List.of(jvg, classic, 2, ts.get(0).id()),
                        List.of(salta, green, -3, ts.get(0).id()), List.of(jvg, green, 3, ts.get(0).id()),
                        List.of(salta, classic, -1, ts.get(1).id()), List.of(centro, classic, 1, ts.get(1).id()));

        service.reopen(id, ACTOR);
        assertStillPlanned();
    }

    @Test
    void zeroActualAddsNoMovementForThatPack() {
        service.close(id, new CloseRequest(LocalDate.of(2026, 11, 10), List.of(new Actual(green, 0)), null));

        assertThat(movementRepo.all()).singleElement().extracting(StockMovement::packId).isEqualTo(classic);
        assertThat(productionRepo.findById(id).orElseThrow().closedOn()).isEqualTo(LocalDate.of(2026, 11, 10));
    }

    @Test
    void distributionAboveActualIs400AndStaysPlanned() {
        assertInvalid(() -> service.close(id, new CloseRequest(null, List.of(),
                        List.of(new Distribution(jvg, classic, 4), new Distribution(jvg, classic, 2)))),
                "distribution", "Se reparten 6 de Clásico pero se produjeron 5");
        assertStillPlanned();
    }

    @Test
    void negativeActualIs400() {
        assertInvalid(() -> service.close(id, new CloseRequest(null, List.of(new Actual(classic, -1)), null)),
                "lines", "Las cantidades reales deben ser enteros ≥ 0");
        assertStillPlanned();
    }

    @Test
    void actualOfPackNotInProductionIs400() {
        assertInvalid(() -> service.close(id, new CloseRequest(null, List.of(new Actual(other, 1)), null)),
                "lines", "Otro no está en esta producción");
    }

    @Test
    void distributionOfPackNotInProductionIs400() {
        assertInvalid(() -> service.close(id, new CloseRequest(null, List.of(),
                        List.of(new Distribution(jvg, other, 1)))),
                "distribution", "Otro no está en esta producción");
        assertInvalid(() -> service.close(id, new CloseRequest(null, List.of(),
                        List.of(new Distribution(jvg, classic, -1)))),
                "distribution", "El reparto debe ser en enteros ≥ 0");
        assertThatThrownBy(() -> service.close(id, new CloseRequest(null, List.of(),
                List.of(new Distribution(999, classic, 1))))).isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Sede no encontrada");
        assertStillPlanned();
    }

    @Test
    void noKitchenIs400AndDistributionToKitchenIs400() {
        assertInvalid(() -> service.close(id, new CloseRequest(null, List.of(),
                        List.of(new Distribution(salta, classic, 1)))),
                "distribution", "El reparto es hacia otras sedes");
        branchRepo.softDelete(salta, ACTOR);
        assertInvalid(() -> service.close(id, new CloseRequest(null, null, null)),
                "branch", "No hay una sede marcada como cocina");
        assertStillPlanned();
    }

    @Test
    void closeTwiceIs409() {
        closeWithTwoToJvg();
        assertConflict(this::closeWithTwoToJvg, "status", "La producción ya está cerrada");
        assertThat(movementRepo.all()).hasSize(4);
    }

    @Test
    void closeInClosedMonthIs409() {
        closes.closes.put(YearMonth.of(2026, 11), new MonthClose(YearMonth.of(2026, 11), BigDecimal.ZERO, TODAY));
        assertConflict(() -> service.close(id, new CloseRequest(null, null, null)), "date", "Ese mes está cerrado");
        assertStillPlanned();
    }

    @Test
    void closeDateBeforeStartOrInTheFutureIs400() {
        assertInvalid(() -> service.close(id, new CloseRequest(LocalDate.of(2026, 11, 2), null, null)),
                "date", "La fecha de cierre no puede ser anterior al inicio");
        assertInvalid(() -> service.close(id, new CloseRequest(TODAY.plusDays(1), null, null)),
                "date", "La fecha de cierre no puede ser futura");
        assertStillPlanned();
    }

    @Test
    void repeatedPackInActualsIs400() {
        assertInvalid(() -> service.close(id, new CloseRequest(null,
                        List.of(new Actual(classic, 10), new Actual(classic, 40)), null)),
                "lines", "Cada pack va una sola vez en el cierre");
        assertStillPlanned();
    }

    @Test
    void reopenUndoesStockAndTransfers() {
        closeWithTwoToJvg();

        ProductionSummary s = service.reopen(id, ACTOR);

        assertThat(s.status()).isEqualTo(ProductionStatus.PLANNED);
        assertThat(s.closedOn()).isNull();
        assertStillPlanned();
        assertThat(productionRepo.findById(id).orElseThrow().lines()).extracting(ProductionLine::planned)
                .containsExactly(5, 5);
        // closes again after reopening
        closeWithTwoToJvg();
        assertThat(movementRepo.all()).hasSize(4);
    }

    @Test
    void reopenAfterOtherMovementIs409() {
        closeWithTwoToJvg();
        movementRepo.add(new StockMovement(null, TODAY, jvg, id, classic, -1, MovementType.SALE, 77L, null));

        assertConflict(() -> service.reopen(id, ACTOR), null,
                "Los packs de este lote ya se movieron; no se puede reabrir");
        assertThat(movementRepo.all()).hasSize(5);
        assertThat(transferRepo.findBySourceProduction(id)).hasSize(1);
        assertThat(productionRepo.findById(id).orElseThrow().status()).isEqualTo(ProductionStatus.CLOSED);
    }

    @Test
    void reopenRejectsPlannedInitialAndClosedMonth() {
        assertConflict(() -> service.reopen(id, ACTOR), "status", "La producción no está cerrada");
        assertStillPlanned();

        closeWithTwoToJvg();
        Production closed = productionRepo.findById(id).orElseThrow();
        closes.closes.put(YearMonth.of(2026, 11), new MonthClose(YearMonth.of(2026, 11), BigDecimal.ZERO, TODAY));
        assertConflict(() -> service.reopen(id, ACTOR), "date", "Ese mes está cerrado");
        closes.closes.clear();
        assertThat(productionRepo.findById(id).orElseThrow()).isEqualTo(closed);
        assertThat(movementRepo.all()).hasSize(4);
        assertThat(transferRepo.findBySourceProduction(id)).hasSize(1);

        Production initial = production(0);
        service.close(initial.id(), new CloseRequest(null, null, null));
        Production initialClosed = productionRepo.findById(initial.id()).orElseThrow();
        assertConflict(() -> service.reopen(initial.id(), ACTOR), "status", "El lote inicial no se reabre");
        assertThat(productionRepo.findById(initial.id()).orElseThrow()).isEqualTo(initialClosed);
        assertThat(movementRepo.findByProduction(initial.id())).hasSize(2);
    }

    @Test
    void closeTakesTheStockLock() {
        closeWithTwoToJvg();
        assertThat(lock.calls).isEqualTo(1);
        service.reopen(id, ACTOR);
        assertThat(lock.calls).isEqualTo(2);
    }
}
