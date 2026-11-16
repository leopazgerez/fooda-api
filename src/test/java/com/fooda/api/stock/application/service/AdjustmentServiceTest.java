package com.fooda.api.stock.application.service;

import com.fooda.api.catalog.fakes.InMemoryBranchRepository;
import com.fooda.api.catalog.fakes.InMemoryPackRepository;
import com.fooda.api.catalog.fakes.InMemorySettingsRepository;
import com.fooda.api.core.domain.catalog.*;
import com.fooda.api.core.domain.finance.MonthClose;
import com.fooda.api.core.domain.production.*;
import com.fooda.api.core.domain.stock.MovementType;
import com.fooda.api.core.domain.stock.StockMovement;
import com.fooda.api.production.fakes.*;
import com.fooda.api.shared.DirectTransactions;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.shared.application.exception.ResourceNotFoundException;
import com.fooda.api.shared.domain.ConflictException;
import com.fooda.api.shared.domain.ValidationException;
import com.fooda.api.stock.application.port.in.AdjustmentRequest;
import com.fooda.api.stock.application.port.in.AdjustmentUseCase;
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

class AdjustmentServiceTest {

    private static final LocalDate D = LocalDate.of(2026, 11, 3);
    private static final Settings SETTINGS = new Settings(new BigDecimal("45000"), new BigDecimal("20"), 4L, 1, 1,
            new BigDecimal("1000"), 3, 30, BigDecimal.TEN, List.of(), List.of(Settings.FIXED_EXPENSE_TYPE), null);

    private final InMemoryStockMovementRepository movements = new InMemoryStockMovementRepository();
    private final InMemoryBranchRepository branches = new InMemoryBranchRepository();
    private final InMemoryPackRepository packs = new InMemoryPackRepository();
    private final InMemoryProductionRepository productions = new InMemoryProductionRepository();
    private final InMemorySettingsRepository settings = new InMemorySettingsRepository();
    private final FixedMonthCloses closes = new FixedMonthCloses();
    private final NoopStockLock lock = new NoopStockLock();
    private final AdjustmentUseCase service = new AdjustmentService(movements, branches, packs, productions, settings,
            closes, lock, new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), ZoneOffset.UTC)),
            new DirectTransactions());

    private final long classic = packs.save(new Pack(null, 1, "Clásico", true, null, null)).id();
    private final long salta = branches.save(new Branch(null, "Salta", "", 10, 1, 1,
            new Shipping(ShippingType.AMOUNT, BigDecimal.ZERO), false, false, null)).id();
    private final long lot;
    private final long planned;

    {
        settings.save(SETTINGS);
        lot = productions.save(new Production(null, "1 NOV 26", 1, ProductionStatus.CLOSED, D, D, List.of(),
                ProductionSnapshot.empty(SETTINGS), null, null, null)).id();
        planned = productions.save(new Production(null, "2 NOV 26", 2, ProductionStatus.PLANNED, D, null, List.of(),
                ProductionSnapshot.empty(SETTINGS), null, null, null)).id();
        movements.add(new StockMovement(null, D, salta, lot, classic, 3, MovementType.PRODUCTION, lot, null));
    }

    private AdjustmentRequest req(Long branch, Long pack, Long prod, Integer qty, String reason) {
        return new AdjustmentRequest(D, branch, pack, prod, qty, reason);
    }

    private void assertInvalid(AdjustmentRequest r, String message) {
        assertThatThrownBy(() -> service.create(r)).isInstanceOf(ValidationException.class).hasMessage(message);
    }

    @Test
    void createStoresTrimmedAdjustment() {
        StockMovement m = service.create(req(salta, classic, lot, -2, "  rotura  "));
        assertThat(m.type()).isEqualTo(MovementType.ADJUSTMENT);
        assertThat(m.refId()).isNull();
        assertThat(m.reason()).isEqualTo("rotura");
        assertThat(m.quantity()).isEqualTo(-2);
        assertThat(movements.findAll()).hasSize(2);
    }

    @Test
    void adjustmentWithoutProductionCreatesInitialLotOnce() {
        StockMovement a = service.create(req(salta, classic, null, 5, "stock previo"));
        StockMovement b = service.create(req(salta, classic, null, 2, "otro"));
        List<Production> initial = productions.findAll().stream().filter(p -> p.number() == 0).toList();
        assertThat(initial).hasSize(1);
        Production p = initial.get(0);
        assertThat(p.code()).isEqualTo("INICIAL");
        assertThat(p.status()).isEqualTo(ProductionStatus.CLOSED);
        assertThat(p.lines()).isEmpty();
        assertThat(p.start()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(p.closedOn()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(a.productionId()).isEqualTo(p.id());
        assertThat(b.productionId()).isEqualTo(p.id());
    }

    @Test
    void adjustmentValidationsInMockOrder() {
        assertThatThrownBy(() -> service.create(req(99L, classic, lot, 1, "x")))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Sede no encontrada");
        assertThatThrownBy(() -> service.create(req(salta, 99L, lot, 1, "x")))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Pack no encontrado");
        assertInvalid(req(salta, classic, lot, null, "x"), "La cantidad debe ser un entero distinto de cero");
        assertInvalid(req(salta, classic, lot, 0, "x"), "La cantidad debe ser un entero distinto de cero");
        assertInvalid(req(salta, classic, lot, 1, "  "), "Indicá el motivo del ajuste");
        assertThatThrownBy(() -> service.create(req(salta, classic, 99L, 1, "x")))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Producción no encontrada");
        assertThatThrownBy(() -> service.create(req(salta, classic, planned, 1, "x")))
                .isInstanceOf(ConflictException.class).hasMessage("La producción no está cerrada");
        assertInvalid(req(salta, classic, lot, -4, "x"), "El ajuste deja el stock en negativo");
        assertThat(movements.findAll()).hasSize(1);
        assertThat(productions.findAll()).hasSize(2);
    }

    @Test
    void dateCannotBeFutureNorBeforeTheLotClosed() {
        assertThatThrownBy(() -> service.create(new AdjustmentRequest(LocalDate.of(2026, 11, 16), salta, classic, lot,
                1, "x"))).isInstanceOf(ValidationException.class).hasMessage("La fecha no puede ser futura");
        assertThatThrownBy(() -> service.create(new AdjustmentRequest(D.minusDays(1), salta, classic, lot, 1, "x")))
                .isInstanceOf(ValidationException.class).hasMessage("La fecha no puede ser anterior al cierre del lote");
        assertThat(service.create(new AdjustmentRequest(D.minusDays(30), salta, classic, null, 1, "conteo"))
                .date()).isEqualTo(D.minusDays(30));
        assertThat(movements.findAll()).hasSize(2);
    }

    @Test
    void inactivePackOnlyAcceptsNegativeAdjustments() {
        packs.save(new Pack(classic, 1, "Clásico", false, null, 0L));
        assertThatThrownBy(() -> service.create(req(salta, classic, lot, 1, "x")))
                .isInstanceOf(ConflictException.class).hasMessage("El pack está inactivo");
        assertThat(service.create(req(salta, classic, lot, -1, "baja")).quantity()).isEqualTo(-1);
    }

    @Test
    void invalidRequestNeverCreatesInitialLot() {
        assertInvalid(req(salta, classic, null, 0, "x"), "La cantidad debe ser un entero distinto de cero");
        assertThat(productions.findAll()).hasSize(2);
    }

    @Test
    void adjustmentInClosedMonthIs409() {
        closes.closes.put(YearMonth.of(2026, 11), new MonthClose(YearMonth.of(2026, 11), BigDecimal.ZERO, D));
        assertThatThrownBy(() -> service.create(req(salta, classic, lot, 1, "x")))
                .isInstanceOf(ConflictException.class).hasMessage("Ese mes está cerrado");
        assertThat(movements.findAll()).hasSize(1);
    }

    @Test
    void annulNegativeAdjustmentRestoresStock() {
        long id = service.create(req(salta, classic, lot, -2, "rotura")).id();
        service.annul(id, 7L);
        assertThat(movements.findAll()).hasSize(1);
    }

    @Test
    void annulPositiveAdjustmentAlreadyTransferredIs409() {
        long id = service.create(req(salta, classic, lot, 2, "hallazgo")).id();
        movements.add(new StockMovement(null, D, salta, lot, classic, -5, MovementType.TRANSFER_OUT, 1L, null));
        assertThatThrownBy(() -> service.annul(id, 7L)).isInstanceOf(ConflictException.class)
                .hasMessage("Borrar el ajuste deja el stock en negativo");
        assertThat(movements.findAll()).hasSize(3);
    }

    @Test
    void annulNonAdjustmentMovementIs404() {
        long prod = movements.findAll().get(0).id();
        assertThatThrownBy(() -> service.annul(prod, 7L)).isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Ajuste no encontrado");
        assertThatThrownBy(() -> service.annul(999L, 7L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void annulInClosedMonthIs409() {
        long id = service.create(req(salta, classic, lot, -1, "x")).id();
        closes.closes.put(YearMonth.of(2026, 11), new MonthClose(YearMonth.of(2026, 11), BigDecimal.ZERO, D));
        assertThatThrownBy(() -> service.annul(id, 7L)).isInstanceOf(ConflictException.class)
                .hasMessage("Ese mes está cerrado");
    }

    @Test
    void writesTakeTheStockLock() {
        long id = service.create(req(salta, classic, lot, 1, "x")).id();
        service.annul(id, 7L);
        assertThat(lock.calls).isEqualTo(2);
    }

    @Test
    void hugeQuantityIs400() {
        assertInvalid(req(salta, classic, lot, 100_001, "x"), "La cantidad es demasiado grande");
        assertInvalid(req(salta, classic, lot, -100_001, "x"), "La cantidad es demasiado grande");
        assertInvalid(req(salta, classic, lot, Integer.MIN_VALUE, "x"), "La cantidad es demasiado grande");
    }
}
