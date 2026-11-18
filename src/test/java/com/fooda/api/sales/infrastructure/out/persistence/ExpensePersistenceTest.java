package com.fooda.api.sales.infrastructure.out.persistence;

import com.fooda.api.TestcontainersConfiguration;
import com.fooda.api.config.JpaAuditingConfig;
import com.fooda.api.config.TimeConfig;
import com.fooda.api.core.domain.expense.Expense;
import com.fooda.api.core.domain.expense.PaymentMethod;
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
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ExpensePersistenceAdapter.class, SalePersistenceAdapter.class, JpaAuditingConfig.class, TimeConfig.class,
        TestcontainersConfiguration.class})
class ExpensePersistenceTest {

    @Autowired ExpensePersistenceAdapter expenses;
    @Autowired SalePersistenceAdapter soldPacks;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    long actor;

    @BeforeEach
    void setUp() {
        Db.reset(jdbc);
        actor = Actors.insertUser(jdbc);
        Actors.actAs(actor);
    }

    @AfterEach
    void clear() {
        Actors.clear();
    }

    private long purchase() {
        long supplier = Rows.supplier(jdbc, actor);
        return jdbc.queryForObject("insert into purchase (supplier_id, status, created_at, created_by, updated_at, updated_by)"
                + " values (?, 'pending', now(), ?, now(), ?) returning purchase_id", Long.class, supplier, actor, actor);
    }

    @Test
    void expenseByPurchaseAndSoftDelete() {
        long purchase = purchase();
        Expense e = expenses.add(new Expense(null, LocalDate.of(2026, 3, 3), " fijo ", "Compra", new BigDecimal("1500.50"),
                new BigDecimal("1.5"), new BigDecimal("1000.00"), PaymentMethod.PACKS, false, "n", purchase, null, null));
        expenses.add(new Expense(null, LocalDate.of(2026, 3, 4), "luz", "Luz", new BigDecimal("10"), null, null,
                PaymentMethod.CASH, true, null, null, null, null));
        em.flush();
        em.clear();

        Expense found = expenses.findByPurchase(purchase).orElseThrow();
        assertThat(found.id()).isEqualTo(e.id());
        assertThat(found.type()).isEqualTo("fijo");
        assertThat(found.amount()).isEqualByComparingTo("1500.5");
        assertThat(found.method()).isEqualTo(PaymentMethod.PACKS);
        assertThat(found.paid()).isFalse();
        assertThat(expenses.findAll()).extracting(Expense::type).containsExactly("luz", "fijo");

        expenses.softDelete(e.id(), actor);
        em.flush();
        em.clear();
        assertThat(expenses.findByPurchase(purchase)).isEmpty();
        assertThat(expenses.findAll()).hasSize(1);
        assertThat(jdbc.queryForObject("select deleted_by from expense where expense_id = ?", Long.class, e.id())).isEqualTo(actor);
        assertThat(soldPacks.soldByProduction()).isEmpty();
    }

    @Test
    void findBetweenAndMonthsMatchFilteringFindAll() {
        LocalDate from = LocalDate.of(2026, 3, 1);
        LocalDate to = LocalDate.of(2026, 3, 31);
        Long deleted = null;
        for (LocalDate d : java.util.List.of(LocalDate.of(2026, 2, 28), to, from, LocalDate.of(2026, 3, 15),
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 3, 20), LocalDate.of(2025, 11, 5))) {
            deleted = expenses.add(new Expense(null, d, "luz", "Luz", new BigDecimal("10"), null, null,
                    PaymentMethod.CASH, true, null, null, null, null)).id();
        }
        expenses.add(new Expense(null, from, "fijo", "Alquiler", new BigDecimal("20"), null, null,
                PaymentMethod.CASH, true, null, null, null, null));
        expenses.softDelete(deleted, actor); // the 2025-11 one
        expenses.softDelete(expenses.findAll().stream().filter(e -> e.date().equals(LocalDate.of(2026, 3, 20)))
                .findFirst().orElseThrow().id(), actor);
        em.flush();
        em.clear();

        assertThat(expenses.findBetween(from, to)).isEqualTo(expenses.findAll().stream()
                .filter(e -> !e.date().isBefore(from) && !e.date().isAfter(to)).toList()).hasSize(4);
        assertThat(expenses.months()).isEqualTo(expenses.findAll().stream().map(e -> java.time.YearMonth.from(e.date()))
                .collect(java.util.stream.Collectors.toSet())).hasSize(3);
    }
}
