package com.fooda.api.stock.application.service;

import com.fooda.api.catalog.fakes.InMemoryBranchRepository;
import com.fooda.api.catalog.fakes.InMemoryPackRepository;
import com.fooda.api.core.application.port.out.StockLock;
import com.fooda.api.core.domain.catalog.*;
import com.fooda.api.core.domain.finance.MonthClose;
import com.fooda.api.core.domain.production.*;
import com.fooda.api.core.domain.stock.MovementType;
import com.fooda.api.core.domain.stock.OutLine;
import com.fooda.api.core.domain.stock.StockMovement;
import com.fooda.api.core.domain.stock.Transfer;
import com.fooda.api.core.domain.stock.TransferLine;
import com.fooda.api.production.fakes.*;
import com.fooda.api.shared.DirectTransactions;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.exception.ResourceNotFoundException;
import com.fooda.api.shared.domain.ConflictException;
import com.fooda.api.shared.domain.ValidationException;
import com.fooda.api.stock.application.port.in.TransferRequest;
import com.fooda.api.stock.application.port.in.TransferUseCase;
import com.fooda.api.stock.application.port.in.TransferView;
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

class TransferServiceTest {

    private static final LocalDate D = LocalDate.of(2026, 11, 3);

    private final InMemoryTransferRepository transfers = new InMemoryTransferRepository();
    private final InMemoryStockMovementRepository movements = new InMemoryStockMovementRepository();
    private final InMemoryBranchRepository branches = new InMemoryBranchRepository();
    private final InMemoryPackRepository packs = new InMemoryPackRepository();
    private final InMemoryProductionRepository productions = new InMemoryProductionRepository();
    private final FixedMonthCloses closes = new FixedMonthCloses();
    private final NoopStockLock lock = new NoopStockLock();
    private final TransferUseCase service = new TransferService(transfers, movements, branches, packs, productions,
            closes, lock, new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), ZoneOffset.UTC)),
            new DirectTransactions());

    private final long classic = packs.save(new Pack(null, 1, "Clásico", true, null, null)).id();
    private final long salta = branches.save(branch("Salta")).id();
    private final long jvg = branches.save(branch("JVG")).id();
    private final long lot;

    {
        Settings s = new Settings(new BigDecimal("45000"), new BigDecimal("20"), 4L, 1, 1,
                new BigDecimal("1000"), 3, 30, BigDecimal.TEN, List.of(), List.of(Settings.FIXED_EXPENSE_TYPE), null);
        lot = productions.save(new Production(null, "1 NOV 26", 1, ProductionStatus.CLOSED, D, D, List.of(),
                ProductionSnapshot.empty(s), null, null, null)).id();
        stock(salta, 3);
    }

    private static Branch branch(String name) {
        return new Branch(null, name, "", 10, 1, 1, new Shipping(ShippingType.AMOUNT, BigDecimal.ZERO), false, false,
                null);
    }

    private void stock(long branch, int qty) {
        movements.add(new StockMovement(null, D, branch, lot, classic, qty, MovementType.PRODUCTION, lot, null));
    }

    private TransferRequest req(Long from, Long to, OutLine... lines) {
        return new TransferRequest(D, from, to, "  hola  ", List.of(lines));
    }

    private void assertInvalid(TransferRequest r, String message) {
        assertThatThrownBy(() -> service.create(r)).isInstanceOf(ValidationException.class).hasMessage(message);
        assertThat(movements.findAll()).hasSize(1);
    }

    @Test
    void createMovesStockBetweenBranches() {
        TransferView v = service.create(req(salta, jvg, new OutLine(lot, classic, 2)));
        long id = v.transfer().id();
        assertThat(v.origin()).isEqualTo("Salta");
        assertThat(v.destination()).isEqualTo("JVG");
        assertThat(v.packs()).isEqualTo(2);
        assertThat(v.transfer().notes()).isEqualTo("hola");
        assertThat(v.transfer().sourceProductionId()).isNull();
        assertThat(v.lines()).containsExactly(new TransferView.LineView(lot, "1 NOV 26", classic, "Clásico", 2));
        assertThat(movements.findTransferLegs(id)).extracting(StockMovement::branchId, StockMovement::quantity,
                        StockMovement::type, StockMovement::refId)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(salta, -2, MovementType.TRANSFER_OUT, id),
                        org.assertj.core.groups.Tuple.tuple(jvg, 2, MovementType.TRANSFER_IN, id));
    }

    @Test
    void createValidationsInMockOrder() {
        OutLine one = new OutLine(lot, classic, 1);
        assertThatThrownBy(() -> service.create(req(99L, jvg, one))).isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Sede no encontrada");
        assertThatThrownBy(() -> service.create(req(salta, null, one))).isInstanceOf(ResourceNotFoundException.class);
        assertInvalid(req(salta, salta, one), "Origen y destino deben ser distintos");
        assertInvalid(req(salta, jvg), "Agregá al menos un pack");
        assertInvalid(req(salta, jvg, new OutLine(lot, classic, 2), new OutLine(lot, classic, 2)),
                "Solo hay 3 packs de Clásico (1 NOV 26) en Salta");
        assertThat(transfers.findAll()).isEmpty();
    }

    @Test
    void createDateCannotBeFutureNorBeforeTheLotClosed() {
        OutLine one = new OutLine(lot, classic, 1);
        assertInvalid(new TransferRequest(LocalDate.of(2026, 11, 16), salta, jvg, null, List.of(one)),
                "La fecha no puede ser futura");
        assertInvalid(new TransferRequest(D.minusDays(1), salta, jvg, null, List.of(one)),
                "La fecha no puede ser anterior al cierre del lote");
    }

    @Test
    void createInClosedMonthIs409() {
        closes.closes.put(YearMonth.of(2026, 11), new MonthClose(YearMonth.of(2026, 11), BigDecimal.ZERO, D));
        assertThatThrownBy(() -> service.create(req(salta, jvg, new OutLine(lot, classic, 1))))
                .isInstanceOf(ConflictException.class).hasMessage("Ese mes está cerrado");
        assertThat(movements.findAll()).hasSize(1);
    }

    @Test
    void annulRemovesTransferAndLegs() {
        long id = service.create(req(salta, jvg, new OutLine(lot, classic, 2))).transfer().id();
        service.annul(id, 1L);
        assertThat(transfers.findAll()).isEmpty();
        assertThat(movements.findTransferLegs(id)).isEmpty();
        assertThat(movements.findAll()).hasSize(1);
        assertThatThrownBy(() -> service.annul(id, 1L)).isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Traslado no encontrado");
    }

    @Test
    void annulDistributionTransferIs409() {
        Transfer t = transfers.add(new Transfer(null, D, salta, jvg, null, lot,
                List.of(new TransferLine(lot, classic, 1))));
        assertThatThrownBy(() -> service.annul(t.id(), 1L)).isInstanceOf(ConflictException.class)
                .hasMessage("Es un traslado de reparto: reabrí la producción o hacé un traslado inverso");
    }

    @Test
    void annulWhenDestinationAlreadyMovedPacksIs409AndKeepsEverything() {
        long id = service.create(req(salta, jvg, new OutLine(lot, classic, 2))).transfer().id();
        service.create(req(jvg, salta, new OutLine(lot, classic, 1)));
        int before = movements.findAll().size();
        assertThatThrownBy(() -> service.annul(id, 1L)).isInstanceOf(ConflictException.class)
                .hasMessage("En JVG ya no quedan 2 packs de Clásico (1 NOV 26)");
        assertThat(movements.findAll()).hasSize(before);
        assertThat(transfers.findById(id)).isPresent();
    }

    @Test
    void listSortsByDateDescByDefault() {
        service.create(new TransferRequest(LocalDate.of(2026, 11, 5), salta, jvg, null,
                List.of(new OutLine(lot, classic, 1))));
        service.create(new TransferRequest(LocalDate.of(2026, 11, 9), salta, jvg, null,
                List.of(new OutLine(lot, classic, 1))));
        assertThat(service.list(new ListQuery(0, 20, null, null)).items())
                .extracting(v -> v.transfer().date())
                .containsExactly(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 5));
    }

    @Test
    void writesTakeTheStockLock() {
        long id = service.create(req(salta, jvg, new OutLine(lot, classic, 1))).transfer().id();
        service.annul(id, 1L);
        assertThat(lock.calls).isEqualTo(2);
    }

    @Test
    void annulInClosedMonthIs409() {
        long id = service.create(req(salta, jvg, new OutLine(lot, classic, 2))).transfer().id();
        closes.closes.put(YearMonth.of(2026, 11), new MonthClose(YearMonth.of(2026, 11), BigDecimal.ZERO, D));
        assertThatThrownBy(() -> service.annul(id, 1L)).isInstanceOfSatisfying(ConflictException.class,
                e -> assertThat(e.field()).isEqualTo("date")).hasMessage("Ese mes está cerrado");
        assertThat(transfers.findAll()).hasSize(1);
        assertThat(movements.findTransferLegs(id)).hasSize(2);
    }
}
