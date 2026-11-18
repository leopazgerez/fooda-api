package com.fooda.api.sales.infrastructure.out.persistence;

import com.fooda.api.TestcontainersConfiguration;
import com.fooda.api.config.JpaAuditingConfig;
import com.fooda.api.config.TimeConfig;
import com.fooda.api.core.domain.expense.PaymentMethod;
import com.fooda.api.core.domain.sales.Customer;
import com.fooda.api.core.domain.sales.Payment;
import com.fooda.api.core.domain.sales.Sale;
import com.fooda.api.core.domain.sales.SaleLine;
import com.fooda.api.core.domain.sales.SaleType;
import com.fooda.api.core.application.port.out.SaleCriteria;
import com.fooda.api.shared.application.ListQuery;
import com.fooda.api.shared.application.Listing;
import com.fooda.api.support.Actors;
import com.fooda.api.support.Db;
import com.fooda.api.support.Rows;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SalePersistenceAdapter.class, CustomerPersistenceAdapter.class, PaymentPersistenceAdapter.class,
        JpaAuditingConfig.class, TimeConfig.class, TestcontainersConfiguration.class})
class SalesPersistenceTest {

    @Autowired SalePersistenceAdapter sales;
    @Autowired CustomerPersistenceAdapter customers;
    @Autowired PaymentPersistenceAdapter payments;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    long actor;
    long branch;
    long pack;
    long lot;

    @BeforeEach
    void setUp() {
        Db.reset(jdbc);
        actor = Actors.insertUser(jdbc);
        Actors.actAs(actor);
        branch = Rows.branch(jdbc, actor);
        pack = Rows.pack(jdbc, actor);
        lot = Rows.production(jdbc, actor);
    }

    @AfterEach
    void clear() {
        Actors.clear();
    }

    private void reload() {
        em.flush();
        em.clear();
    }

    private Sale sale(SaleType type, Long customerId, int qty) {
        return new Sale(null, LocalDate.of(2026, 3, 3), branch, customerId, type, type == SaleType.INTERNAL_USE ? "Regalo" : null,
                PaymentMethod.CASH, new BigDecimal("5.00"), new BigDecimal("2.50"), "n",
                List.of(new SaleLine(lot, pack, qty, new BigDecimal("100.00"))));
    }

    @Test
    void saleRoundTripWithLinesAndSoftDeleteIncludesLines() {
        Sale s = sales.add(sale(SaleType.INTERNAL_USE, null, 3));
        reload();

        Sale found = sales.findById(s.id()).orElseThrow();
        assertThat(found.type()).isEqualTo(SaleType.INTERNAL_USE);
        assertThat(found.reason()).isEqualTo("Regalo");
        assertThat(found.method()).isEqualTo(PaymentMethod.CASH);
        assertThat(found.discount()).isEqualByComparingTo("5");
        assertThat(found.lines()).hasSize(1);
        assertThat(found.lines().get(0).quantity()).isEqualTo(3);
        assertThat(sales.findAll()).hasSize(1);

        sales.softDelete(s.id(), actor);
        reload();
        assertThat(sales.findById(s.id())).isEmpty();
        assertThat(sales.findAll()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from sale_line where deleted_at is not null", Integer.class)).isEqualTo(1);
    }

    @Test
    void soldByProductionCountsOnlyLiveIncomeSales() {
        sales.add(sale(SaleType.RETAIL, null, 4));
        sales.add(sale(SaleType.INTERNAL_USE, null, 7));
        Sale voided = sales.add(sale(SaleType.RETAIL, null, 9));
        sales.softDelete(voided.id(), actor);
        reload();

        assertThat(sales.soldByProduction()).containsExactly(java.util.Map.entry(lot, 4));
    }

    @Test
    void customerSaveWithStaleVersionIsConflict() {
        Customer v0 = customers.save(new Customer(null, "Ana", "Pérez", null, null, "03-14", null, null));
        customers.save(v0);
        assertThatThrownBy(() -> customers.save(v0)).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void existsByCustomerIgnoresDeletedSales() {
        long customer = Rows.customer(jdbc, actor);
        Sale s = sales.add(sale(SaleType.RETAIL, customer, 1));
        reload();
        assertThat(sales.existsByCustomer(customer)).isTrue();
        sales.softDelete(s.id(), actor);
        reload();
        assertThat(sales.existsByCustomer(customer)).isFalse();
    }

    @Test
    void paymentSoftDeleteBySale() {
        Sale s = sales.add(sale(SaleType.WHOLESALE, null, 1));
        payments.add(new Payment(null, s.id(), LocalDate.of(2026, 3, 4), new BigDecimal("50.00"), PaymentMethod.TRANSFER));
        reload();
        assertThat(payments.findBySale(s.id())).hasSize(1);
        assertThat(payments.findAll()).hasSize(1);

        payments.softDeleteBySale(s.id(), actor);
        reload();
        assertThat(payments.findBySale(s.id())).isEmpty();
        assertThat(payments.findAll()).isEmpty();
    }

    @Test
    void schemaChecksRejectNegativeAmounts() {
        // each failed statement aborts the test transaction, so quantity and amount are separate tests
        Sale s = sales.add(sale(SaleType.RETAIL, null, 1));
        reload();
        assertThatThrownBy(() -> jdbc.update("insert into sale_line (sale_id, production_id, pack_id, quantity, unit_price,"
                + " created_at, created_by, updated_at, updated_by) values (?, ?, ?, 0, 1, now(), ?, now(), ?)",
                s.id(), lot, pack, actor, actor)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void schemaChecksRejectZeroPaymentAmount() {
        Sale s = sales.add(sale(SaleType.RETAIL, null, 1));
        reload();
        assertThatThrownBy(() -> jdbc.update("insert into payment (sale_id, payment_date, amount, method,"
                + " created_at, created_by, updated_at, updated_by) values (?, current_date, 0, 'cash', now(), ?, now(), ?)",
                s.id(), actor, actor)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void pageMatchesListingOverFindAll() {
        long branch2 = Rows.branch(jdbc, actor);
        long customer = Rows.customer(jdbc, actor);
        SaleType[] types = {SaleType.RETAIL, SaleType.WHOLESALE, SaleType.INTERNAL_USE};
        List<Long> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) {
            SaleType t = types[i % 3];
            Sale base = sale(t, i % 2 == 0 ? customer : null, 1 + i % 4);
            ids.add(sales.add(new Sale(null, LocalDate.of(2026, 3, 1 + i % 6), i % 5 == 0 ? branch2 : branch,
                    base.customerId(), t, base.reason(), base.method(), base.discount(), base.shipping(), "n",
                    base.lines())).id());
        }
        sales.softDelete(ids.get(7), actor);
        reload();

        var sortable = java.util.Map.<String, java.util.Comparator<Sale>>of(
                "date", java.util.Comparator.comparing(Sale::date), "id", java.util.Comparator.comparingLong(Sale::id));
        var byId = java.util.Comparator.comparingLong(Sale::id);
        var criteria = List.of(
                new SaleCriteria(null, null, null, null, null),
                new SaleCriteria(branch2, null, null, null, null),
                new SaleCriteria(null, SaleType.WHOLESALE, null, null, null),
                new SaleCriteria(branch, null, customer, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 4)),
                new SaleCriteria(null, SaleType.RETAIL, null, LocalDate.of(2026, 3, 3), null),
                new SaleCriteria(null, null, null, null, LocalDate.of(2026, 3, 2)));
        for (SaleCriteria c : criteria) {
            var filtered = sales.findAll().stream()
                    .filter(s -> (c.branchId() == null || c.branchId() == s.branchId())
                            && (c.type() == null || c.type() == s.type())
                            && (c.customerId() == null || c.customerId().equals(s.customerId()))
                            && (c.from() == null || !s.date().isBefore(c.from()))
                            && (c.to() == null || !s.date().isAfter(c.to()))).toList();
            for (String sort : new String[]{null, "date", "id"}) {
                for (String dir : new String[]{null, "asc", "desc"}) {
                    for (int page = 0; page < 3; page++) {
                        ListQuery q = new ListQuery(page, 4, sort, dir);
                        var expected = Listing.page(filtered, q, sortable, "date", "desc", byId);
                        var actual = sales.page(c, q);
                        assertThat(actual.items().stream().map(Sale::id).toList())
                                .as("%s %s %s %d", c, sort, dir, page)
                                .isEqualTo(expected.items().stream().map(Sale::id).toList());
                        assertThat(actual.total()).isEqualTo(expected.total());
                        assertThat(actual.items()).allSatisfy(s -> assertThat(s.lines()).hasSize(1));
                    }
                }
            }
        }
        assertThatThrownBy(() -> sales.page(criteria.get(0), new ListQuery(0, 4, "bogus", null)))
                .isInstanceOf(com.fooda.api.shared.domain.ValidationException.class);
    }

    @Test
    void findBetweenAndMonthsMatchFilteringFindAll() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        List<Long> ids = new java.util.ArrayList<>();
        for (LocalDate d : List.of(LocalDate.of(2026, 2, 28), from, LocalDate.of(2026, 3, 15), to,
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 3, 20))) {
            Sale base = sale(SaleType.RETAIL, null, 1);
            Sale s = sales.add(new Sale(null, d, branch, null, base.type(), null, base.method(), base.discount(),
                    base.shipping(), null, base.lines()));
            ids.add(s.id());
            payments.add(new Payment(null, s.id(), d.plusDays(1), new BigDecimal("1.00"), PaymentMethod.CASH));
        }
        sales.softDelete(ids.get(5), actor);
        payments.softDeleteBySale(ids.get(5), actor);
        sales.add(new Sale(null, LocalDate.of(2025, 12, 1), branch, null, SaleType.RETAIL, null, PaymentMethod.CASH,
                null, null, null, sale(SaleType.RETAIL, null, 1).lines()));
        reload();

        assertThat(sales.findBetween(from, to)).isEqualTo(sales.findAll().stream()
                .filter(s -> !s.date().isBefore(from) && !s.date().isAfter(to)).toList()).hasSize(3);
        assertThat(sales.months()).isEqualTo(sales.findAll().stream().map(s -> java.time.YearMonth.from(s.date()))
                .collect(java.util.stream.Collectors.toSet())).hasSize(4);
        assertThat(payments.findBetween(from, to)).isEqualTo(payments.findAll().stream()
                .filter(p -> !p.date().isBefore(from) && !p.date().isAfter(to)).toList()).hasSize(3);
        assertThat(payments.months()).isEqualTo(payments.findAll().stream().map(p -> java.time.YearMonth.from(p.date()))
                .collect(java.util.stream.Collectors.toSet())).hasSize(2);
    }

    @Test
    void findWithCustomerMatchesFilteringFindAll() {
        long customer = Rows.customer(jdbc, actor);
        long other = Rows.customer(jdbc, actor);
        Sale a = sales.add(sale(SaleType.RETAIL, customer, 1));
        sales.add(sale(SaleType.RETAIL, null, 2));
        sales.add(sale(SaleType.WHOLESALE, other, 3));
        Sale deleted = sales.add(sale(SaleType.RETAIL, customer, 1));
        sales.add(sale(SaleType.RETAIL, customer, 4));
        sales.softDelete(deleted.id(), actor);
        reload();

        assertThat(sales.findWithCustomer()).isEqualTo(sales.findAll().stream()
                .filter(s -> s.customerId() != null).toList()).hasSize(3)
                .first().isEqualTo(sales.findById(a.id()).orElseThrow());
    }
}
