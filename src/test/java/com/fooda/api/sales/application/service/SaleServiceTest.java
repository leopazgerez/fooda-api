package com.fooda.api.sales.application.service;

import com.fooda.api.catalog.fakes.InMemoryBranchRepository;
import com.fooda.api.catalog.fakes.InMemoryPackRepository;
import com.fooda.api.catalog.fakes.InMemorySettingsRepository;
import com.fooda.api.catalog.fakes.InMemorySupplyRepository;
import com.fooda.api.core.domain.catalog.*;
import com.fooda.api.core.domain.expense.PaymentMethod;
import com.fooda.api.core.domain.finance.MonthClose;
import com.fooda.api.core.domain.production.*;
import com.fooda.api.core.domain.sales.Customer;
import com.fooda.api.core.domain.stock.MovementType;
import com.fooda.api.core.domain.stock.StockMovement;
import com.fooda.api.production.fakes.*;
import com.fooda.api.sales.application.port.in.SaleFilter;
import com.fooda.api.sales.application.port.in.SaleRequest;
import com.fooda.api.sales.application.port.in.SaleRequest.LineRequest;
import com.fooda.api.sales.application.port.in.SaleUseCase;
import com.fooda.api.sales.application.port.in.SaleView;
import com.fooda.api.sales.fakes.InMemoryCustomerRepository;
import com.fooda.api.sales.fakes.InMemoryPaymentRepository;
import com.fooda.api.sales.fakes.InMemorySaleRepository;
import com.fooda.api.shared.DirectTransactions;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.shared.application.ListQuery;
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

class SaleServiceTest {

    private static final LocalDate D = LocalDate.of(2026, 11, 3);

    private final InMemorySaleRepository sales = new InMemorySaleRepository();
    private final InMemoryPaymentRepository payments = new InMemoryPaymentRepository();
    private final InMemoryExpenseRepository expenses = new InMemoryExpenseRepository();
    private final InMemoryCustomerRepository customers = new InMemoryCustomerRepository();
    private final InMemoryBranchRepository branches = new InMemoryBranchRepository();
    private final InMemoryPackRepository packs = new InMemoryPackRepository();
    private final InMemoryProductionRepository productions = new InMemoryProductionRepository();
    private final InMemorySupplyRepository supplies = new InMemorySupplyRepository();
    private final InMemorySettingsRepository settings = new InMemorySettingsRepository();
    private final InMemoryStockMovementRepository movements = new InMemoryStockMovementRepository();
    private final FixedMonthCloses closes = new FixedMonthCloses();
    private final NoopStockLock lock = new NoopStockLock();
    private final SaleUseCase service = new SaleService(sales, payments, expenses, customers, branches, packs,
            productions, supplies, settings, movements, closes, lock,
            new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), ZoneOffset.UTC)),
            new DirectTransactions());

    private final long classic = packs.save(new Pack(null, 1, "Clásico", true, null, null)).id();
    private final long jvg = branches.save(branch("JVG", new Shipping(ShippingType.AMOUNT, new BigDecimal("9000"))))
            .id();
    private final long salta = branches.save(branch("Salta", new Shipping(ShippingType.AMOUNT, BigDecimal.ZERO))).id();
    private final long lot;
    private final Settings st = new Settings(new BigDecimal("45000"), new BigDecimal("20"), 4L, 1, 1,
            new BigDecimal("24000"), 3, 30, BigDecimal.TEN, List.of(), List.of(Settings.FIXED_EXPENSE_TYPE), null);

    {
        settings.save(st);
        // direct cost per pack: estimated 10000, actual 14000 (paid)
        Supply flour = new Supply(1L, "Harina", Unit.G, 1, null, BigDecimal.ONE, null, null, null, true, null);
        Dish dish = new Dish(1L, "Plato", List.of(new RecipeLine(1L, BigDecimal.ONE, Unit.G, BigDecimal.ONE)), null);
        ProductionSnapshot snap = new ProductionSnapshot(java.util.Map.of(1L, new BigDecimal("10000")),
                java.util.Map.of(1L, dish), List.of(new SnapshotPack(classic, 1L, List.of(new MenuSlot(1, 1L)))), st,
                List.of(flour));
        lot = productions.save(new Production(null, "1 NOV 26", 1, ProductionStatus.CLOSED, D, D, List.of(), snap,
                java.util.Map.of(1L, new BigDecimal("14000")), null, null)).id();
        stock(jvg, 10);
    }

    private static Branch branch(String name, Shipping s) {
        return new Branch(null, name, "", 10, 1, 1, s, false, false, null);
    }

    private void stock(long branch, int qty) {
        movements.add(new StockMovement(null, D, branch, lot, classic, qty, MovementType.PRODUCTION, lot, null));
    }

    private LineRequest line(int qty, String price) {
        return new LineRequest(lot, classic, qty, price == null ? null : new BigDecimal(price));
    }

    private SaleRequest req(String type, String discount, String shipping, String notes, LineRequest... lines) {
        return new SaleRequest(D, jvg, null, type, "cash", null, discount == null ? null : new BigDecimal(discount),
                shipping == null ? null : new BigDecimal(shipping), notes, null, List.of(lines));
    }

    private SaleRequest retail(LineRequest... lines) {
        return req("retail", null, null, null, lines);
    }

    private void assertInvalid(SaleRequest r, String message) {
        assertThatThrownBy(() -> service.create(r)).isInstanceOf(ValidationException.class).hasMessage(message);
    }

    private void assertNoSideEffects() {
        assertThat(sales.findAll()).isEmpty();
        assertThat(payments.findAll()).isEmpty();
        assertThat(expenses.findAll()).isEmpty();
        assertThat(movements.findAll()).hasSize(1);
    }

    @Test
    void retailSaleMovesStockCreatesOnePaymentForTheTotal() {
        SaleView v = service.create(req("retail", "4000", "1000", "  nota  ", line(2, "45000")));
        assertThat(v.total()).isEqualByComparingTo("87000");
        assertThat(v.packs()).isEqualTo(2);
        assertThat(v.branch()).isEqualTo("JVG");
        assertThat(v.customer()).isNull();
        assertThat(v.sale().notes()).isEqualTo("nota");
        assertThat(v.lines()).extracting(SaleView.LineView::lot, SaleView.LineView::pack, SaleView.LineView::quantity)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("1 NOV 26", "Clásico", 2));
        assertThat(movements.findBySale(v.sale().id())).extracting(StockMovement::branchId, StockMovement::quantity,
                        StockMovement::type, StockMovement::date)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(jvg, -2, MovementType.SALE, D));
        assertThat(payments.findBySale(v.sale().id())).hasSize(1).first().satisfies(p -> {
            assertThat(p.amount()).isEqualByComparingTo("87000");
            assertThat(p.method()).isEqualTo(PaymentMethod.CASH);
            assertThat(p.date()).isEqualTo(D);
        });
        assertThat(expenses.findAll()).isEmpty();
        assertThat(service.get(v.sale().id()).payments()).hasSize(1);
        assertThat(service.list(null, new ListQuery(0, 10, null, null)).items().get(0).payments()).isEmpty();
    }

    @Test
    void priceDefaultsToPackPriceThenSettings() {
        packs.save(new Pack(null, 2, "Premium", true, new BigDecimal("52000"), null));
        long premium = packs.findAll().stream().filter(p -> p.name().equals("Premium")).findFirst().get().id();
        stock(jvg, 0 + 1);
        movements.add(new StockMovement(null, D, jvg, lot, premium, 1, MovementType.PRODUCTION, lot, null));
        SaleView v = service.create(retail(new LineRequest(lot, premium, 1, null)));
        assertThat(v.total()).isEqualByComparingTo("52000");
        assertThat(service.create(retail(line(1, null))).total()).isEqualByComparingTo("45000");
    }

    @Test
    void giftWithZeroPriceHasNoPayment() {
        SaleView v = service.create(retail(line(1, "0")));
        assertThat(v.total()).isEqualByComparingTo("0");
        assertThat(payments.findAll()).isEmpty();
        assertThat(movements.findBySale(v.sale().id())).hasSize(1);
    }

    @Test
    void paidWithPacksRequiresNotesAndCreatesPacksExpense() {
        assertInvalid(req("paid_with_packs", null, null, "  ", line(1, null)), "Indicá qué recibiste a cambio");
        SaleRequest r = new SaleRequest(D, jvg, null, "paid_with_packs", "cash", null, null, null, " 10 kg de pollo ",
                " materia prima ", List.of(line(1, null)));
        SaleView v = service.create(r);
        assertThat(v.total()).isEqualByComparingTo("0");
        assertThat(payments.findAll()).isEmpty();
        assertThat(expenses.findBySale(v.sale().id())).get().satisfies(e -> {
            assertThat(e.type()).isEqualTo("materia prima");
            assertThat(e.detail()).isEqualTo("10 kg de pollo");
            assertThat(e.method()).isEqualTo(PaymentMethod.PACKS);
            assertThat(e.paid()).isTrue();
            assertThat(e.date()).isEqualTo(D);
            assertThat(e.amount()).isEqualByComparingTo("45000");
            assertThat(e.amountPacks()).isEqualByComparingTo("1");
            assertThat(e.packPrice()).isEqualByComparingTo("45000");
        });
        SaleRequest noType = new SaleRequest(D, jvg, null, "paid_with_packs", "cash", null, null, null, "x", " ",
                List.of(line(1, null)));
        assertThat(expenses.findBySale(service.create(noType).sale().id())).get()
                .extracting(e -> e.type()).isEqualTo("personal");
    }

    @Test
    void paidWithPacksLongTextIs400() {
        assertInvalid(req("paid_with_packs", null, null, "x".repeat(201), line(1, null)),
                "Las notas del pago con packs tienen hasta 200 caracteres");
        assertInvalid(new SaleRequest(D, jvg, null, "paid_with_packs", "cash", null, null, null, "n",
                "y".repeat(61), List.of(line(1, null))), "El tipo de gasto tiene hasta 60 caracteres");
        assertNoSideEffects();
    }

    @Test
    void internalUseNeedsNoNotesAndCreatesNoExpenseNorPayment() {
        SaleView v = service.create(req("internal_use", "500", "500", null, line(2, "99")));
        assertThat(v.total()).isEqualByComparingTo("0");
        assertThat(v.sale().discount()).isEqualByComparingTo("0");
        assertThat(v.lines().get(0).unitPrice()).isEqualByComparingTo("0");
        assertThat(payments.findAll()).isEmpty();
        assertThat(expenses.findAll()).isEmpty();
        assertThat(movements.findBySale(v.sale().id())).hasSize(1);
    }

    @Test
    void saleDatedBeforeThePacksReachedTheBranchIs400() {
        long centro = branches.save(branch("Centro", new Shipping(ShippingType.AMOUNT, BigDecimal.ZERO))).id();
        movements.add(new StockMovement(null, D.plusDays(5), centro, lot, classic, 4, MovementType.TRANSFER_IN, 9L,
                null));
        int before = movements.findAll().size();
        assertInvalid(new SaleRequest(D.plusDays(2), centro, null, "retail", "cash", null, null, null, null, null,
                List.of(line(1, "1"))), "Solo hay 0 packs de Clásico (1 NOV 26) en Centro");
        assertThat(movements.findAll()).hasSize(before);
        assertThat(service.create(new SaleRequest(D.plusDays(5), centro, null, "retail", "cash", null, null, null,
                null, null, List.of(line(1, "1")))).sale().date()).isEqualTo(D.plusDays(5));
    }

    @Test
    void twoLinesOverStockIs400WithoutSideEffects() {
        assertInvalid(retail(line(6, "1"), line(6, "1")), "Solo hay 10 packs de Clásico (1 NOV 26) en JVG");
        assertNoSideEffects();
    }

    @Test
    void validationsInMockOrder() {
        assertThat(lock.calls).isZero();
        SaleRequest ok = retail(line(1, "1"));
        assertThatThrownBy(() -> service.create(new SaleRequest(D, 99L, null, "zzz", "zzz", null, null, null, null,
                null, List.of()))).isInstanceOf(ResourceNotFoundException.class).hasMessage("Sede no encontrada");
        assertThatThrownBy(() -> service.create(new SaleRequest(D, jvg, 99L, "zzz", "zzz", null, null, null, null,
                null, List.of()))).isInstanceOf(ResourceNotFoundException.class).hasMessage("Cliente no encontrado");
        assertThatThrownBy(() -> service.create(new SaleRequest(D, jvg, null, "zzz", "zzz", null, null, null, null,
                null, List.of()))).isInstanceOf(ValidationException.class).hasMessage("Tipo de venta inválido");
        assertThatThrownBy(() -> service.create(new SaleRequest(D, jvg, null, "retail", "zzz", null, null, null, null,
                null, List.of()))).isInstanceOf(ValidationException.class).hasMessage("Medio de pago inválido");
        assertInvalid(retail(), "Agregá al menos un pack");
        assertInvalid(req("paid_with_packs", null, null, null), "Agregá al menos un pack");
        assertInvalid(req("paid_with_packs", null, null, null, line(1, null)), "Indicá qué recibiste a cambio");
        assertThatThrownBy(() -> service.create(retail(new LineRequest(lot, 99L, 1, null))))
                .isInstanceOf(ResourceNotFoundException.class).hasMessage("Pack no encontrado");
        assertInvalid(retail(line(0, "1")), "Las cantidades deben ser enteros mayores a cero");
        assertInvalid(retail(line(1, "-1")), "Revisá los precios");
        assertInvalid(retail(line(11, "1")), "Solo hay 10 packs de Clásico (1 NOV 26) en JVG");
        assertInvalid(new SaleRequest(LocalDate.of(2026, 11, 16), jvg, null, "retail", "cash", null, null, null, null,
                null, List.of(line(1, "1"))), "La fecha no puede ser futura");
        assertInvalid(new SaleRequest(D.minusDays(1), jvg, null, "retail", "cash", null, null, null, null, null,
                List.of(line(1, "1"))), "La fecha no puede ser anterior al cierre del lote");
        assertNoSideEffects();
        assertThat(service.create(ok)).isNotNull();
    }

    @Test
    void discountOverTotalIs400() {
        assertInvalid(req("retail", "100", null, null, line(1, "50")), "El descuento supera el total");
        assertNoSideEffects();
    }

    @Test
    void closedMonthIs409() {
        closes.closes.put(YearMonth.of(2026, 11), new MonthClose(YearMonth.of(2026, 11), BigDecimal.ZERO, D));
        assertThatThrownBy(() -> service.create(retail(line(1, "1")))).isInstanceOf(ConflictException.class)
                .hasMessage("Ese mes está cerrado");
        assertNoSideEffects();
        assertThatThrownBy(() -> service.annul(1L, 1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void annulRestoresStockAndDeletesPaymentAndExpense() {
        long a = service.create(retail(line(2, "100"))).sale().id();
        SaleRequest pwp = new SaleRequest(D, jvg, null, "paid_with_packs", "cash", null, null, null, "n", null,
                List.of(line(1, null)));
        long b = service.create(pwp).sale().id();
        assertThat(movements.findAll()).hasSize(3);

        service.annul(a, 1L);
        assertThat(sales.findById(a)).isEmpty();
        assertThat(payments.findBySale(a)).isEmpty();
        assertThat(movements.findBySale(a)).isEmpty();
        service.annul(b, 1L);
        assertThat(expenses.findAll()).isEmpty();
        assertThat(movements.findAll()).hasSize(1);
        assertThatThrownBy(() -> service.annul(b, 1L)).isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Venta no encontrada");
        assertThatThrownBy(() -> service.get(b)).isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Venta no encontrada");

        long c = service.create(retail(line(1, "100"))).sale().id();
        closes.closes.put(YearMonth.of(2026, 11), new MonthClose(YearMonth.of(2026, 11), BigDecimal.ZERO, D));
        assertThatThrownBy(() -> service.annul(c, 1L)).isInstanceOf(ConflictException.class);
        assertThat(sales.findById(c)).isPresent();
    }

    @Test
    void profitUsesActualCostOverheadAndShipping() {
        // sale in an open month; the lot's month is then closed with an overhead of 10000 per pack
        long id = service.create(req("retail", "4000", null, null, line(2, "45000"))).sale().id();
        long internal = service.create(req("internal_use", null, null, null, line(3, null))).sale().id();
        closes.closes.put(YearMonth.of(2026, 11), new MonthClose(YearMonth.of(2026, 11), new BigDecimal("10000"), D));
        // actual 14000 + overhead 10000 = 24000 per pack (estimated 10000 or the 24000 reference would differ)
        assertThat(service.get(id).profit()).isEqualByComparingTo("20000");
        assertThat(service.get(id).profit()).isEqualByComparingTo(
                new BigDecimal("2").multiply(new BigDecimal("45000").subtract(new BigDecimal("2000"))
                        .subtract(new BigDecimal("24000")).subtract(new BigDecimal("9000"))));
        assertThat(service.get(internal).profit()).isEqualByComparingTo("-72000");
    }

    @Test
    void listFiltersBeforeMapping() {
        stock(salta, 5);
        long ana = customers.save(new Customer(null, "Ana", "Paz", null, null, null, null, null)).id();
        service.create(retail(line(1, "10")));
        service.create(new SaleRequest(D.plusDays(2), salta, ana, "wholesale", "cash", null, null, null, null, null,
                List.of(line(1, "10"))));
        service.create(new SaleRequest(D.plusDays(4), jvg, null, "internal_use", "cash", null, null, null, null, null,
                List.of(line(1, "10"))));
        ListQuery q = new ListQuery(0, 10, null, null);
        assertThat(service.list(null, q).items()).extracting(v -> v.sale().id()).containsExactly(3L, 2L, 1L);
        assertThat(service.list(new SaleFilter(jvg, null, null, null, null), q).items())
                .extracting(v -> v.sale().id()).containsExactly(3L, 1L);
        assertThat(service.list(new SaleFilter(null, "wholesale", null, null, null), q).items())
                .extracting(v -> v.sale().id()).containsExactly(2L);
        SaleView withCustomer = service.list(new SaleFilter(null, null, ana, null, null), q).items().get(0);
        assertThat(withCustomer.customer()).isEqualTo("Ana Paz");
        assertThat(service.list(new SaleFilter(null, null, null, D.plusDays(2), D.plusDays(2)), q).items())
                .extracting(v -> v.sale().id()).containsExactly(2L);
        var paged = service.list(new SaleFilter(null, null, null, null, null), new ListQuery(0, 1, "id", "asc"));
        assertThat(paged.total()).isEqualTo(3);
        assertThat(paged.items()).extracting(v -> v.sale().id()).containsExactly(1L);
    }

    @Test
    void writesTakeTheStockLock() {
        long id = service.create(retail(line(1, "1"))).sale().id();
        assertThat(lock.calls).isEqualTo(1);
        service.annul(id, 1L);
        assertThat(lock.calls).isEqualTo(2);
    }
}
