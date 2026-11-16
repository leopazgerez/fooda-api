package com.fooda.api.production.infrastructure.out.persistence;

import com.fooda.api.TestcontainersConfiguration;
import com.fooda.api.config.JpaAuditingConfig;
import com.fooda.api.config.TimeConfig;
import com.fooda.api.core.domain.catalog.Composite;
import com.fooda.api.core.domain.catalog.CompositeRule;
import com.fooda.api.core.domain.catalog.Dish;
import com.fooda.api.core.domain.catalog.MenuSlot;
import com.fooda.api.core.domain.catalog.PackagingType;
import com.fooda.api.core.domain.catalog.RecipeLine;
import com.fooda.api.core.domain.catalog.Settings;
import com.fooda.api.core.domain.catalog.Supply;
import com.fooda.api.core.domain.catalog.Unit;
import com.fooda.api.core.domain.production.Production;
import com.fooda.api.core.domain.production.ProductionLine;
import com.fooda.api.core.domain.production.ProductionSnapshot;
import com.fooda.api.core.domain.production.ProductionStatus;
import com.fooda.api.core.domain.production.Purchase;
import com.fooda.api.core.domain.production.PurchaseLine;
import com.fooda.api.core.domain.production.PurchaseStatus;
import com.fooda.api.core.domain.production.SnapshotPack;
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
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ProductionPersistenceAdapter.class, PurchasePersistenceAdapter.class, JpaAuditingConfig.class,
        TimeConfig.class, TestcontainersConfiguration.class})
class ProductionPersistenceTest {

    @Autowired ProductionPersistenceAdapter productions;
    @Autowired PurchasePersistenceAdapter purchases;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    long actor;
    long pack1;
    long pack2;

    private void reload() {
        em.flush();
        em.clear();
    }

    @BeforeEach
    void setUp() {
        Db.reset(jdbc);
        actor = Actors.insertUser(jdbc);
        Actors.actAs(actor);
        pack1 = Rows.pack(jdbc, actor);
        pack2 = Rows.pack(jdbc, actor);
    }

    @AfterEach
    void clear() {
        Actors.clear();
    }

    private Settings settings() {
        return new Settings(new BigDecimal("45000"), new BigDecimal("12.5"), null, 2, 3, new BigDecimal("1000.50"),
                6, 30, new BigDecimal("0.1"), List.of("promo"), List.of("fijo", "luz"), 3L);
    }

    private ProductionSnapshot snapshot() {
        Dish dish = new Dish(7L, "Lasaña", List.of(new RecipeLine(1L, new BigDecimal("100.500"), Unit.G, new BigDecimal("2.000"))), 2L);
        Supply supply = new Supply(1L, "Bandeja", Unit.U, 5L, "x10", new BigDecimal("10.000"), null,
                PackagingType.TRAY, new Composite(CompositeRule.MAX, List.of(2L, 3L)), true, 1L);
        return new ProductionSnapshot(Map.of(1L, new BigDecimal("1234.5678901234")), Map.of(7L, dish),
                List.of(new SnapshotPack(pack1, 9L, List.of(new MenuSlot(1, 7L)))), settings(), List.of(supply));
    }

    private Production production(List<ProductionLine> lines) {
        return new Production(null, "P-001", 1, ProductionStatus.PLANNED, LocalDate.of(2026, 3, 1), null, lines,
                snapshot(), Map.of(), Map.of(), null);
    }

    @Test
    void snapshotRoundTripKeepsDecimalsDishesAndSettings() {
        long id = productions.save(production(List.of(new ProductionLine(pack1, 5, null)))).id();
        reload();

        Production p = productions.findById(id).orElseThrow();
        ProductionSnapshot s = p.snapshot();
        assertThat(s.costs().get(1L)).isEqualByComparingTo("1234.5678901234");
        assertThat(s.costs().get(1L).scale()).isEqualTo(10);
        assertThat(s.dishes().get(7L).recipe().get(0).unit()).isEqualTo(Unit.G);
        assertThat(s.dishes().get(7L).recipe().get(0).quantity()).isEqualByComparingTo("100.5");
        assertThat(s.settings().packPrice()).isEqualByComparingTo("45000");
        assertThat(s.settings().expenseTypes()).containsExactly("fijo", "luz");
        assertThat(s.packs()).containsExactly(new SnapshotPack(pack1, 9L, List.of(new MenuSlot(1, 7L))));
        Supply supply = s.supplies().get(0);
        assertThat(supply.packagingType()).isEqualTo(PackagingType.TRAY);
        assertThat(supply.composite()).isEqualTo(new Composite(CompositeRule.MAX, List.of(2L, 3L)));
    }

    @Test
    void paidCostsAndLeftoversRoundTrip() {
        Production base = production(List.of());
        Production p = productions.save(new Production(null, "P-002", 2, ProductionStatus.CLOSED, base.start(),
                LocalDate.of(2026, 3, 9), List.of(), base.snapshot(),
                Map.of(4L, new BigDecimal("12.3456789012")), Map.of(5L, new BigDecimal("0.250")), null));
        reload();

        Production found = productions.findById(p.id()).orElseThrow();
        assertThat(found.paidCosts().get(4L)).isEqualByComparingTo("12.3456789012");
        assertThat(found.leftovers().get(5L)).isEqualByComparingTo("0.25");
        assertThat(found.status()).isEqualTo(ProductionStatus.CLOSED);
        assertThat(found.closedOn()).isEqualTo(LocalDate.of(2026, 3, 9));
    }

    @Test
    void linesReplacedKeepHistory() {
        Production p = productions.save(production(List.of(new ProductionLine(pack1, 5, null), new ProductionLine(pack2, 3, null))));
        reload();

        productions.save(new Production(p.id(), p.code(), p.number(), p.status(), p.start(), null,
                List.of(new ProductionLine(pack1, 5, 4), new ProductionLine(pack2, 3, null)), p.snapshot(),
                p.paidCosts(), p.leftovers(), p.version()));
        reload();

        assertThat(productions.findById(p.id()).orElseThrow().lines())
                .containsExactly(new ProductionLine(pack1, 5, 4), new ProductionLine(pack2, 3, null));
        assertThat(jdbc.queryForObject("select count(*) from production_line where production_id = ?", Integer.class, p.id())).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from production_line where production_id = ? and deleted_at is not null and deleted_by = ?",
                Integer.class, p.id(), actor)).isEqualTo(2);
    }

    @Test
    void maxNumberCountsAnnulledLots() {
        Production p = productions.save(production(List.of(new ProductionLine(pack1, 5, null))));
        productions.softDelete(p.id(), actor);
        reload();
        assertThat(productions.maxNumber(YearMonth.of(2026, 3))).isEqualTo(1);
        assertThat(productions.maxNumber(YearMonth.of(2026, 4))).isZero();
    }

    @Test
    void unchangedLinesAreNotReplacedAndSoftDeleteIncludesLines() {
        Production p = productions.save(production(List.of(new ProductionLine(pack1, 5, null))));
        reload();
        productions.save(productions.findById(p.id()).orElseThrow());
        reload();
        assertThat(jdbc.queryForObject("select count(*) from production_line", Integer.class)).isEqualTo(1);

        productions.softDelete(p.id(), actor);
        reload();
        assertThat(productions.findById(p.id())).isEmpty();
        assertThat(productions.findAll()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from production_line where deleted_by = ?", Integer.class, actor)).isEqualTo(1);
    }

    @Test
    void purchaseKeepsIdOnResaveAndReplacesLines() {
        long production = productions.save(production(List.of())).id();
        long supplier = Rows.supplier(jdbc, actor);
        long supply = Rows.supply(jdbc, actor, supplier);
        Purchase saved = purchases.save(new Purchase(null, production, supplier, PurchaseStatus.PENDING, null,
                List.of(new PurchaseLine(supply, new BigDecimal("2.500"), new BigDecimal("100.00"))), null, null));
        reload();

        Purchase again = purchases.save(new Purchase(saved.id(), production, supplier, PurchaseStatus.BOUGHT,
                LocalDate.of(2026, 3, 2),
                List.of(new PurchaseLine(supply, new BigDecimal("3"), new BigDecimal("100"))), new BigDecimal("300.00"), saved.version()));
        reload();

        assertThat(again.id()).isEqualTo(saved.id());
        Purchase found = purchases.findById(saved.id()).orElseThrow();
        assertThat(found.status()).isEqualTo(PurchaseStatus.BOUGHT);
        assertThat(found.total()).isEqualByComparingTo("300");
        assertThat(found.lines()).hasSize(1);
        assertThat(found.lines().get(0).quantity()).isEqualByComparingTo("3");
        assertThat(jdbc.queryForObject("select count(*) from purchase_line where deleted_by = ?", Integer.class, actor)).isEqualTo(1);
        assertThat(purchases.findByProduction(production)).hasSize(1);
        assertThat(purchases.findAll()).hasSize(1);

        purchases.softDelete(saved.id(), actor);
        reload();
        assertThat(purchases.findById(saved.id())).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from purchase_line where deleted_at is not null", Integer.class)).isEqualTo(2);
    }

    @Test
    void staleVersionIsAConflict() {
        Production p = productions.save(production(List.of(new ProductionLine(pack1, 5, null))));
        reload();
        productions.save(new Production(p.id(), p.code(), p.number(), p.status(), p.start(), null, p.lines(),
                p.snapshot(), p.paidCosts(), Map.of(1L, BigDecimal.ONE), p.version()));
        reload();
        assertThatThrownBy(() -> productions.save(p)).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void stalePurchaseVersionIsAConflict() {
        long supplier = Rows.supplier(jdbc, actor);
        long supply = Rows.supply(jdbc, actor, supplier);
        Purchase p = purchases.save(new Purchase(null, null, supplier, PurchaseStatus.PENDING, null,
                List.of(new PurchaseLine(supply, BigDecimal.ONE, BigDecimal.TEN)), null, null));
        reload();
        purchases.save(new Purchase(p.id(), null, supplier, PurchaseStatus.BOUGHT, LocalDate.of(2026, 3, 2),
                p.lines(), BigDecimal.TEN, p.version()));
        reload();
        assertThatThrownBy(() -> purchases.save(p)).isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void usageChecksSeeOnlyLiveRows() {
        long supplier = Rows.supplier(jdbc, actor);
        long otherSupplier = Rows.supplier(jdbc, actor);
        long supply = Rows.supply(jdbc, actor, supplier);
        long otherSupply = Rows.supply(jdbc, actor, supplier);
        Production prod = productions.save(production(List.of(new ProductionLine(pack1, 5, null))));
        Purchase p = purchases.save(new Purchase(null, prod.id(), supplier, PurchaseStatus.PENDING, null,
                List.of(new PurchaseLine(supply, BigDecimal.ONE, BigDecimal.TEN)), null, null));
        reload();
        assertThat(purchases.isSupplyUsed(supply)).isTrue();
        assertThat(purchases.isSupplyUsed(otherSupply)).isFalse();
        assertThat(purchases.isSupplierUsed(supplier)).isTrue();
        assertThat(purchases.isSupplierUsed(otherSupplier)).isFalse();
        assertThat(productions.isPackUsed(pack1)).isTrue();
        assertThat(productions.isPackUsed(pack2)).isFalse();

        // a live line under a soft-deleted parent doesn't count
        jdbc.update("update purchase set deleted_at = now(), deleted_by = ? where purchase_id = ?", actor, p.id());
        jdbc.update("update production set deleted_at = now(), deleted_by = ? where production_id = ?", actor, prod.id());
        reload();
        assertThat(purchases.isSupplyUsed(supply)).isFalse();
        assertThat(purchases.isSupplierUsed(supplier)).isFalse();
        assertThat(productions.isPackUsed(pack1)).isFalse();
    }

    @Test
    void findByProductionLoadsOnlyItsLines() {
        long a = productions.save(production(List.of())).id();
        long b = productions.save(new Production(null, "P-009", 9, ProductionStatus.PLANNED, LocalDate.of(2026, 3, 1),
                null, List.of(), snapshot(), Map.of(), Map.of(), null)).id();
        long supplier = Rows.supplier(jdbc, actor);
        long supply = Rows.supply(jdbc, actor, supplier);
        purchases.save(new Purchase(null, a, supplier, PurchaseStatus.PENDING, null,
                List.of(new PurchaseLine(supply, BigDecimal.ONE, BigDecimal.TEN)), null, null));
        purchases.save(new Purchase(null, b, supplier, PurchaseStatus.PENDING, null,
                List.of(new PurchaseLine(supply, new BigDecimal("2"), BigDecimal.TEN)), null, null));
        reload();
        assertThat(purchases.findByProduction(a)).singleElement()
                .satisfies(x -> assertThat(x.lines()).singleElement()
                        .satisfies(l -> assertThat(l.quantity()).isEqualByComparingTo("1")));
        assertThat(purchases.findByProduction(999)).isEmpty();
    }
}
