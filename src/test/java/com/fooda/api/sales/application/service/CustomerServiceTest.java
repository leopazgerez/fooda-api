package com.fooda.api.sales.application.service;

import com.fooda.api.catalog.fakes.InMemoryPackRepository;
import com.fooda.api.catalog.fakes.InMemorySettingsRepository;
import com.fooda.api.core.domain.catalog.Pack;
import com.fooda.api.core.domain.catalog.Settings;
import com.fooda.api.core.domain.sales.Sale;
import com.fooda.api.core.domain.sales.SaleLine;
import com.fooda.api.core.domain.sales.SaleType;
import com.fooda.api.sales.application.port.in.CustomerInput;
import com.fooda.api.sales.application.port.in.CustomerView;
import com.fooda.api.sales.fakes.InMemoryCustomerRepository;
import com.fooda.api.sales.fakes.InMemorySaleRepository;
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
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CustomerServiceTest {

    private final InMemoryCustomerRepository customers = new InMemoryCustomerRepository();
    private final InMemorySaleRepository sales = new InMemorySaleRepository();
    private final InMemoryPackRepository packs = new InMemoryPackRepository();
    private final InMemorySettingsRepository settings = new InMemorySettingsRepository();
    private final com.fooda.api.production.fakes.NoopStockLock lock = new com.fooda.api.production.fakes.NoopStockLock();
    private final CustomerService service = new CustomerService(customers, sales, packs, settings, lock,
            new BusinessCalendar(Clock.fixed(Instant.parse("2026-11-15T15:00:00Z"), ZoneOffset.UTC)),
            new DirectTransactions());

    CustomerServiceTest() {
        settings.save(new Settings(new BigDecimal("45000"), BigDecimal.ONE, null, 1, 1, BigDecimal.ONE, 2, 30,
                BigDecimal.ONE, List.of(), List.of("fijo"), null));
    }

    private static <T> Field<T> f(T v) { return Field.of(v); }

    private static CustomerInput in(String first, String last, String birthday) {
        return new CustomerInput(f(first), f(last), Field.absent(), Field.absent(), f(birthday), Field.absent(),
                Field.absent());
    }

    private long customer(String first, String last, String birthday) {
        return service.create(in(first, last, birthday)).customer().id();
    }

    private Sale sale(LocalDate date, Long customerId, SaleType type, long packId, int qty, String price) {
        return sales.add(new Sale(null, date, 1, customerId, type, null, null, null, null, null,
                List.of(new SaleLine(1, packId, qty, new BigDecimal(price)))));
    }

    @Test
    void viewCountsOnlyIncomeSales() {
        long id = customer("Ana", "Paz", null);
        sale(LocalDate.of(2026, 9, 10), id, SaleType.RETAIL, 1, 2, "100");
        sale(LocalDate.of(2026, 10, 5), id, SaleType.WHOLESALE, 1, 3, "100");
        sale(LocalDate.of(2026, 11, 1), id, SaleType.INTERNAL_USE, 1, 9, "100");

        CustomerView v = service.get(id);

        assertThat(v.packs()).isEqualTo(5);
        assertThat(v.total()).isEqualByComparingTo("500");
        assertThat(v.lastPurchase()).isEqualTo(LocalDate.of(2026, 10, 5));
        assertThat(v.regular()).isTrue();
        assertThat(v.fullName()).isEqualTo("Ana Paz");
    }

    @Test
    void listSortsByFullNameIgnoringCase() {
        customer("beto", "Zeta", null);
        customer("Ana", "Paz", null);
        var page = service.list(new ListQuery(0, 10, null, null));
        assertThat(page.items()).extracting(CustomerView::fullName).containsExactly("Ana Paz", "beto Zeta");
    }

    @Test
    void createNormalizesBirthdayAndRequiresNames() {
        assertThat(service.create(in("Ana", "Paz", "1990-03-05")).customer().birthday())
                .isEqualTo("03-05");
        assertThatThrownBy(() -> service.create(in(" ", "Paz", null))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.create(in("Ana", null, null))).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.get(99)).hasMessage("Cliente no encontrado");
    }

    @Test
    void patchKeepsAbsentFieldsAndClearsNullOnes() {
        long id = service.create(new CustomerInput(f("Ana"), f("Paz"), f("111"), f("Calle 1"), f("03-05"), f("vip"),
                Field.absent())).customer().id();

        var v = service.update(id, new CustomerInput(Field.absent(), Field.absent(), f(null), Field.absent(),
                f(null), Field.absent(), Field.absent())).customer();

        assertThat(v.phone()).isNull();
        assertThat(v.birthday()).isNull();
        assertThat(v.address()).isEqualTo("Calle 1");
        assertThat(v.notes()).isEqualTo("vip");
        assertThat(v.firstName()).isEqualTo("Ana");
        assertThatThrownBy(() -> service.update(id, new CustomerInput(f(null), Field.absent(), Field.absent(),
                Field.absent(), Field.absent(), Field.absent(), Field.absent())))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.update(id, new CustomerInput(f("X"), Field.absent(), Field.absent(),
                Field.absent(), Field.absent(), Field.absent(), f(99L))))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void deleteWithLiveSalesIs409ButAllowedAfterTheyAreAnnulled() {
        long id = customer("Ana", "Paz", null);
        Sale s = sale(LocalDate.of(2026, 11, 1), id, SaleType.RETAIL, 1, 1, "100");

        assertThatThrownBy(() -> service.delete(id, 1))
                .isInstanceOf(ConflictException.class)
                .hasMessage("El cliente tiene ventas; no se puede borrar");

        sales.softDelete(s.id(), 1);
        service.delete(id, 1);
        assertThatThrownBy(() -> service.get(id)).hasMessage("Cliente no encontrado");
    }

    @Test
    void deleteTakesTheStockLock() {
        long id = customer("Ana", "Paz", null);
        service.delete(id, 1);
        assertThat(lock.calls).isEqualTo(1);
    }

    @Test
    void followUpsAndBirthdays() {
        long pack = packs.save(new Pack(null, 1, "Clásico", true, null, null)).id();
        long late = customer("Ana", "Paz", "11-15");
        long recent = customer("Beto", "Gil", null);
        customer("Cris", "Lo", null);
        sales.add(new Sale(null, LocalDate.of(2026, 10, 1), 1, late, SaleType.RETAIL, null, null, null, null, null,
                List.of(new SaleLine(1, pack, 1, new BigDecimal("100")), new SaleLine(1, 999, 1, new BigDecimal("100")))));
        sale(LocalDate.of(2026, 11, 10), recent, SaleType.RETAIL, pack, 1, "100");

        var r = service.followUps();

        assertThat(r.followUps()).hasSize(1);
        var fu = r.followUps().get(0);
        assertThat(fu.customer().fullName()).isEqualTo("Ana Paz");
        assertThat(fu.daysWithoutPurchase()).isEqualTo(45);
        assertThat(fu.packs()).containsExactly("Clásico", "—");
        assertThat(r.birthdays()).extracting(CustomerView::fullName).containsExactly("Ana Paz");
    }
}
