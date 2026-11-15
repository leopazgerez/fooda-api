package com.fooda.api.catalog.infrastructure.out.persistence;

import com.fooda.api.TestcontainersConfiguration;
import com.fooda.api.config.JpaAuditingConfig;
import com.fooda.api.config.TimeConfig;
import com.fooda.api.core.domain.catalog.Composite;
import com.fooda.api.core.domain.catalog.CompositeRule;
import com.fooda.api.core.domain.catalog.Supplier;
import com.fooda.api.core.domain.catalog.Supply;
import com.fooda.api.core.domain.catalog.SupplyPrice;
import com.fooda.api.core.domain.catalog.Unit;
import com.fooda.api.support.Actors;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({SupplyPersistenceAdapter.class, SupplierPersistenceAdapter.class, JpaAuditingConfig.class, TimeConfig.class,
        TestcontainersConfiguration.class})
class SupplyPersistenceTest {

    @Autowired SupplyPersistenceAdapter supplies;
    @Autowired SupplierPersistenceAdapter suppliers;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    private void reload() {
        em.flush();
        em.clear();
    }

    long actor;
    long supplierId;

    @BeforeEach
    void actor() {
        actor = Actors.insertUser(jdbc);
        Actors.actAs(actor);
        supplierId = suppliers.save(new Supplier(null, "Súper", null, 1, null)).id();
    }

    @AfterEach
    void clear() {
        Actors.clear();
    }

    private Supply supply(String name, Composite composite) {
        return new Supply(null, name, Unit.G, supplierId, null, new BigDecimal("1000.000"), null, null, composite, true, null);
    }

    private Supply supply(String name) {
        return supply(name, null);
    }

    @Test
    void compositeRoundTripReplacesParts() {
        long a = supplies.save(supply("A")).id();
        long b = supplies.save(supply("B")).id();
        long c = supplies.save(supply("C")).id();
        Supply mix = supplies.save(supply("Mix", new Composite(CompositeRule.AVERAGE, List.of(a, b))));
        reload();
        assertThat(supplies.findById(mix.id()).orElseThrow().composite().partIds()).containsExactly(a, b);
        assertThat(supplies.isPartOfComposite(a)).isTrue();

        Supply resaved = supplies.save(new Supply(mix.id(), "Mix", Unit.G, supplierId, null, mix.content(), null, null,
                new Composite(CompositeRule.AVERAGE, List.of(b, c)), true, mix.version()));
        assertThat(resaved.version()).isGreaterThanOrEqualTo(mix.version());
        reload();
        assertThat(supplies.findById(mix.id()).orElseThrow().composite().partIds()).containsExactly(b, c);
        assertThat(jdbc.queryForObject("select count(*) from supply_part where supply_id = ? and deleted_at is not null and deleted_by = ?",
                Integer.class, mix.id(), actor)).isEqualTo(1);
        assertThat(supplies.isPartOfComposite(a)).isFalse();

        supplies.softDelete(mix.id(), actor);
        reload();
        assertThat(jdbc.queryForObject("select count(*) from supply where supply_id = ? and deleted_at is not null", Integer.class, mix.id())).isEqualTo(1);
        assertThat(supplies.isPartOfComposite(b)).isFalse();
        assertThat(supplies.findById(mix.id())).isEmpty();
    }

    @Test
    void duplicateNameCheckIgnoresCaseAndDeleted() {
        Supply pollo = supplies.save(supply("Pollo"));
        reload();
        assertThat(supplies.existsByNameIgnoreCase("POLLO", null)).isTrue();
        assertThat(supplies.existsByNameIgnoreCase("POLLO", pollo.id())).isFalse();
        assertThat(supplies.existsBySupplier(supplierId)).isTrue();
        supplies.softDelete(pollo.id(), actor);
        assertThat(supplies.existsByNameIgnoreCase("pollo", null)).isFalse();
    }

    @Test
    void pricesOrderedAndSoftDeletable() {
        long id = supplies.save(supply("Arroz")).id();
        SupplyPrice late = supplies.addPrice(new SupplyPrice(null, id, LocalDate.of(2026, 5, 1), new BigDecimal("20.00")));
        SupplyPrice early = supplies.addPrice(new SupplyPrice(null, id, LocalDate.of(2026, 1, 1), new BigDecimal("10.00")));
        reload();
        assertThat(supplies.findPrices(id)).extracting(SupplyPrice::id).containsExactly(early.id(), late.id());
        assertThat(supplies.findPrice(late.id())).contains(late);
        supplies.softDeletePrice(late.id(), actor);
        reload();
        assertThat(jdbc.queryForObject("select count(*) from supply_price where supply_price_id = ? and deleted_at is not null and deleted_by = ?", Integer.class, late.id(), actor)).isEqualTo(1);
        assertThat(supplies.findAllPrices()).extracting(SupplyPrice::id).containsExactly(early.id());
        assertThat(supplies.findPrice(late.id())).isEmpty();
    }
}
