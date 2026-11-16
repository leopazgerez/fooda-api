package com.fooda.api.sales.application.service;

import com.fooda.api.catalog.fakes.InMemorySettingsRepository;
import com.fooda.api.core.domain.catalog.Settings;
import com.fooda.api.core.domain.expense.Expense;
import com.fooda.api.core.domain.expense.PaymentMethod;
import com.fooda.api.core.domain.finance.MonthClose;
import com.fooda.api.production.fakes.FixedMonthCloses;
import com.fooda.api.production.fakes.InMemoryExpenseRepository;
import com.fooda.api.sales.application.port.in.ExpenseInput;
import com.fooda.api.shared.DirectTransactions;
import com.fooda.api.shared.application.BusinessCalendar;
import com.fooda.api.shared.application.Field;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.domain.ConflictException;
import com.fooda.api.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpenseServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);

    private final InMemoryExpenseRepository repo = new InMemoryExpenseRepository();
    private final InMemorySettingsRepository settings = new InMemorySettingsRepository();
    private final FixedMonthCloses closes = new FixedMonthCloses();
    private final ExpenseService service = new ExpenseService(repo, settings, closes,
            new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), ZoneOffset.UTC)),
            new DirectTransactions());

    ExpenseServiceTest() {
        setPackPrice("45000");
    }

    private void setPackPrice(String price) {
        settings.save(new Settings(new BigDecimal(price), BigDecimal.ONE, null, 1, 1, BigDecimal.ONE, 1, 1,
                BigDecimal.ONE, List.of(), List.of("fijo"), null));
    }

    private static <T> Field<T> f(T v) { return Field.of(v); }

    private static ExpenseInput in(Field<LocalDate> date, Field<BigDecimal> amount, Field<BigDecimal> packs) {
        return new ExpenseInput(date, f("fijo"), f("Alquiler"), amount, packs, Field.absent(), Field.absent(),
                Field.absent(), Field.absent());
    }

    private static ExpenseInput patch(Field<BigDecimal> amount, Field<BigDecimal> packs) {
        return new ExpenseInput(Field.absent(), Field.absent(), Field.absent(), amount, packs, Field.absent(),
                Field.absent(), Field.absent(), Field.absent());
    }

    private static BigDecimal d(String v) { return new BigDecimal(v); }

    private Expense pesos() {
        return service.create(in(Field.absent(), f(d("1000")), Field.absent()));
    }

    @Test
    void packsRoundingToZeroIs400() {
        assertThatThrownBy(() -> service.create(in(Field.absent(), Field.absent(), f(d("0.004")))))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Los packs deben ser un número mayor a cero")
                .extracting(e -> ((ValidationException) e).field()).isEqualTo("amountPacks");
    }

    @Test
    void createInPesosDefaultsPaidAndTransfer() {
        Expense e = pesos();
        assertThat(e.date()).isEqualTo(TODAY);
        assertThat(e.paid()).isTrue();
        assertThat(e.method()).isEqualTo(PaymentMethod.TRANSFER);
        assertThat(e.amountPacks()).isNull();
        assertThat(e.packPrice()).isNull();
    }

    @Test
    void createInPacksStoresPacksPriceAndAmount() {
        Expense e = service.create(in(Field.absent(), Field.absent(), f(d("1.5"))));
        assertThat(e.amount()).isEqualByComparingTo("67500");
        assertThat(e.amountPacks()).isEqualByComparingTo("1.5");
        assertThat(e.packPrice()).isEqualByComparingTo("45000");
    }

    @Test
    void amountAndPacksTogetherOrNeitherIs400() {
        assertThatThrownBy(() -> service.create(in(Field.absent(), f(d("1")), f(d("1")))))
                .isInstanceOf(ValidationException.class).hasMessage("Indicá el monto en pesos o en packs, no los dos");
        assertThatThrownBy(() -> service.create(in(Field.absent(), Field.absent(), Field.absent())))
                .isInstanceOf(ValidationException.class).hasMessage("Indicá el monto en pesos o en packs");
        assertThatThrownBy(() -> service.create(in(Field.absent(), Field.absent(), f(d("0")))))
                .isInstanceOf(ValidationException.class).hasMessage("Los packs deben ser un número mayor a cero");
    }

    @Test
    void invalidMethodIs400() {
        var bad = new ExpenseInput(Field.absent(), f("fijo"), f("x"), f(d("1")), Field.absent(), f("bitcoin"),
                Field.absent(), Field.absent(), Field.absent());
        assertThatThrownBy(() -> service.create(bad)).isInstanceOf(ValidationException.class)
                .hasMessage("Medio de pago inválido");
    }

    @Test
    void patchPacksUsesStoredPackPrice() {
        Expense e = service.create(in(Field.absent(), Field.absent(), f(d("1"))));
        setPackPrice("50000");
        assertThat(service.update(e.id(), patch(Field.absent(), f(d("2")))).amount()).isEqualByComparingTo("90000");
        Expense inPesos = pesos();
        assertThat(service.update(inPesos.id(), patch(Field.absent(), f(d("2")))).amount())
                .isEqualByComparingTo("100000");
    }

    @Test
    void patchToPesosClearsPacks() {
        Expense e = service.create(in(Field.absent(), Field.absent(), f(d("1"))));
        Expense u = service.update(e.id(), patch(f(d("500")), Field.absent()));
        assertThat(u.amount()).isEqualByComparingTo("500");
        assertThat(u.amountPacks()).isNull();
        assertThat(u.packPrice()).isNull();
    }

    @Test
    void staleVersionIs409Like() {
        Expense e = pesos();
        service.update(e.id(), patch(f(d("5")), Field.absent()));
        var stale = new ExpenseInput(Field.absent(), Field.absent(), Field.absent(), f(d("6")), Field.absent(),
                Field.absent(), Field.absent(), Field.absent(), f(e.version()));
        assertThatThrownBy(() -> service.update(e.id(), stale)).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void generatedExpenseCannotBeEditedOrDeleted() {
        Expense g = repo.add(new Expense(null, TODAY, "compra", "Compra", d("10"), null, null,
                PaymentMethod.TRANSFER, true, null, 7L, null, null));
        assertThatThrownBy(() -> service.update(g.id(), patch(f(d("5")), Field.absent())))
                .isInstanceOf(ConflictException.class).hasMessage("Este gasto se generó automáticamente");
        assertThatThrownBy(() -> service.delete(g.id(), 1L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void closedMonthBlocksCreatePatchDeleteAndMovingIntoIt() {
        Expense open = pesos();
        closes.closes.put(YearMonth.of(2026, 10), new MonthClose(YearMonth.of(2026, 10), d("1"), TODAY));
        LocalDate oct = LocalDate.of(2026, 10, 5);
        assertThatThrownBy(() -> service.create(in(f(oct), f(d("1")), Field.absent())))
                .isInstanceOf(ConflictException.class).hasMessage("Ese mes está cerrado");
        var move = new ExpenseInput(f(oct), Field.absent(), Field.absent(), Field.absent(), Field.absent(),
                Field.absent(), Field.absent(), Field.absent(), Field.absent());
        assertThatThrownBy(() -> service.update(open.id(), move)).isInstanceOf(ConflictException.class);

        closes.closes.clear();
        Expense old = service.create(in(f(oct), f(d("1")), Field.absent()));
        closes.closes.put(YearMonth.of(2026, 10), new MonthClose(YearMonth.of(2026, 10), d("1"), TODAY));
        assertThatThrownBy(() -> service.update(old.id(), patch(f(d("2")), Field.absent())))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.delete(old.id(), 1L)).isInstanceOf(ConflictException.class);
    }

    @Test
    void listSortsByDateDesc() {
        Expense a = service.create(in(f(LocalDate.of(2026, 11, 1)), f(d("1")), Field.absent()));
        Expense b = service.create(in(f(LocalDate.of(2026, 11, 9)), f(d("1")), Field.absent()));
        assertThat(service.list(new ListQuery(0, 10, null, null)).items()).containsExactly(b, a);
    }

    @Test
    void deleteRemovesAndMissingIs404() {
        Expense e = pesos();
        service.delete(e.id(), 1L);
        assertThatThrownBy(() -> service.get(e.id())).hasMessage("Gasto no encontrado");
    }
}
