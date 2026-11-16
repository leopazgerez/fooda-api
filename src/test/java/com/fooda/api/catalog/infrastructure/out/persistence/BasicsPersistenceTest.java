package com.fooda.api.catalog.infrastructure.out.persistence;

import com.fooda.api.TestcontainersConfiguration;
import com.fooda.api.config.JpaAuditingConfig;
import com.fooda.api.config.TimeConfig;
import com.fooda.api.core.domain.catalog.Branch;
import com.fooda.api.core.domain.catalog.ReserveFund;
import com.fooda.api.core.domain.catalog.Settings;
import com.fooda.api.core.domain.catalog.Shipping;
import com.fooda.api.core.domain.catalog.ShippingType;
import com.fooda.api.core.domain.catalog.Supplier;
import com.fooda.api.support.Actors;
import com.fooda.api.support.Db;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SupplierPersistenceAdapter.class, BranchPersistenceAdapter.class, ReserveFundPersistenceAdapter.class,
        SettingsPersistenceAdapter.class, JpaAuditingConfig.class, TimeConfig.class, TestcontainersConfiguration.class})
class BasicsPersistenceTest {

    @Autowired SupplierPersistenceAdapter suppliers;
    @Autowired BranchPersistenceAdapter branches;
    @Autowired ReserveFundPersistenceAdapter funds;
    @Autowired SettingsPersistenceAdapter settings;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    private void detach() {
        em.flush();
        em.clear();
    }

    long actor;

    @BeforeEach
    void actor() {
        Db.reset(jdbc);
        actor = Actors.insertUser(jdbc);
        Actors.actAs(actor);
    }

    @AfterEach
    void clear() {
        Actors.clear();
    }

    @Test
    void branchRoundTripAndKitchen() {
        Branch saved = branches.save(new Branch(null, "Salta", "", 300, 20, 999,
                new Shipping(ShippingType.AMOUNT, new BigDecimal("0.00")), false, true, null));
        detach();
        assertThat(branches.findById(saved.id())).contains(saved);
        assertThat(branches.findKitchen()).map(Branch::id).contains(saved.id());
    }

    @Test
    void softDeletedRowsDisappearAndRecordWho() {
        Supplier s = suppliers.save(new Supplier(null, "Súper", "jueves", 1, null));
        suppliers.softDelete(s.id(), actor);
        assertThat(suppliers.findById(s.id())).isEmpty();
        assertThat(suppliers.findAll()).isEmpty();
        assertThat(jdbc.queryForObject("select deleted_at is not null from supplier where supplier_id = ?", Boolean.class, s.id())).isTrue();
        assertThat(jdbc.queryForObject("select deleted_by from supplier where supplier_id = ?", Long.class, s.id()))
                .isEqualTo(actor);
    }

    @Test
    void reserveFundRoundTrip() {
        ReserveFund saved = funds.save(new ReserveFund(null, "Fondo", new BigDecimal("5.50"), null));
        detach();
        assertThat(funds.findById(saved.id())).contains(saved);
    }

    @Test
    void settingsRoundTripWithArraysAndNullOil() {
        assertThat(settings.find()).isEmpty();
        Settings saved = settings.save(new Settings(new BigDecimal("1000.00"), new BigDecimal("30.000"), null, 2, 3,
                new BigDecimal("10.00"), 3, 30, new BigDecimal("0.1000"),
                List.of("amigos", "web"), List.of("fijo", "variable"), null));
        detach();
        Settings found = settings.find().orElseThrow();
        assertThat(found).isEqualTo(saved);
        assertThat(found.saleReasons()).containsExactly("amigos", "web");
        assertThat(found.expenseTypes()).containsExactly("fijo", "variable");
        assertThat(found.oilSupplyId()).isNull();
        Settings updated = settings.save(new Settings(new BigDecimal("1200.00"), found.oilMlPerPack(), null, 2, 3,
                found.referenceOverhead(), 3, 30, found.priceRiseAlert(), found.saleReasons(), found.expenseTypes(),
                found.version()));
        detach();
        assertThat(settings.find().orElseThrow().packPrice()).isEqualByComparingTo("1200.00");
        assertThat(updated.packPrice()).isEqualByComparingTo("1200.00");
        assertThat(updated.version()).isGreaterThan(found.version());
    }

    @Test
    void staleVersionIs409() {
        Supplier v0 = suppliers.save(new Supplier(null, "Súper", null, 1, null));
        suppliers.save(v0);
        assertThatThrownBy(() -> suppliers.save(v0)).isInstanceOf(OptimisticLockingFailureException.class);
    }
}
