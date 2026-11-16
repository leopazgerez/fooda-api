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
}
