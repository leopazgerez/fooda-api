package com.fooda.api.catalog.infrastructure.out.persistence;

import com.fooda.api.TestcontainersConfiguration;
import com.fooda.api.config.JpaAuditingConfig;
import com.fooda.api.config.TimeConfig;
import com.fooda.api.core.domain.catalog.Dish;
import com.fooda.api.core.domain.catalog.MenuSlot;
import com.fooda.api.core.domain.catalog.Pack;
import com.fooda.api.core.domain.catalog.PackVersion;
import com.fooda.api.core.domain.catalog.RecipeLine;
import com.fooda.api.core.domain.catalog.Supplier;
import com.fooda.api.core.domain.catalog.Supply;
import com.fooda.api.core.domain.catalog.Unit;
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
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({DishPersistenceAdapter.class, PackPersistenceAdapter.class, SupplyPersistenceAdapter.class,
        SupplierPersistenceAdapter.class, JpaAuditingConfig.class, TimeConfig.class, TestcontainersConfiguration.class})
class DishPackPersistenceTest {

    @Autowired DishPersistenceAdapter dishes;
    @Autowired PackPersistenceAdapter packs;
    @Autowired SupplyPersistenceAdapter supplies;
    @Autowired SupplierPersistenceAdapter suppliers;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    long actor;
    long a;
    long b;

    private void reload() {
        em.flush();
        em.clear();
    }

    @BeforeEach
    void setUp() {
        Db.reset(jdbc);
        actor = Actors.insertUser(jdbc);
        Actors.actAs(actor);
        long supplierId = suppliers.save(new Supplier(null, "Súper", null, 1, null)).id();
        a = supplies.save(new Supply(null, "A", Unit.G, supplierId, null, new BigDecimal("1000.000"), null, null, null, true, null)).id();
        b = supplies.save(new Supply(null, "B", Unit.G, supplierId, null, new BigDecimal("1000.000"), null, null, null, true, null)).id();
    }

    @AfterEach
    void clear() {
        Actors.clear();
    }

    private RecipeLine line(long supplyId, String qty) {
        return new RecipeLine(supplyId, new BigDecimal(qty), Unit.G, new BigDecimal("1.000"));
    }

    private Dish dish(String name) {
        return dishes.save(new Dish(null, name, List.of(line(a, "100.000")), null));
    }

    @Test
    void editingRecipeKeepsHistoryOfOldLines() {
        Dish d = dishes.save(new Dish(null, "Guiso", List.of(line(a, "100.000"), line(b, "50.000")), null));
        reload();
        assertThat(dishes.findById(d.id()).orElseThrow().recipe()).containsExactly(line(a, "100.000"), line(b, "50.000"));

        Dish resaved = dishes.save(new Dish(d.id(), "Guiso 2", List.of(line(b, "70.000")), d.version()));
        assertThat(resaved.version()).isGreaterThanOrEqualTo(d.version());
        reload();
        Dish found = dishes.findById(d.id()).orElseThrow();
        assertThat(found.name()).isEqualTo("Guiso 2");
        assertThat(found.recipe()).containsExactly(line(b, "70.000"));
        assertThat(jdbc.queryForObject("select count(*) from recipe_line where dish_id = ?", Integer.class, d.id())).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from recipe_line where dish_id = ? and deleted_at is not null and deleted_by = ?",
                Integer.class, d.id(), actor)).isEqualTo(2);

        assertThat(dishes.isSupplyUsed(b)).isTrue();
        assertThat(dishes.isSupplyUsed(a)).isFalse();
        dishes.softDelete(d.id(), actor);
        reload();
        assertThat(jdbc.queryForObject("select count(*) from recipe_line where dish_id = ? and deleted_at is null", Integer.class, d.id())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from dish where dish_id = ? and deleted_at is not null", Integer.class, d.id())).isEqualTo(1);
        assertThat(dishes.isSupplyUsed(b)).isFalse();
        assertThat(dishes.findById(d.id())).isEmpty();
    }

    @Test
    void sameRecipeIsNotReplaced() {
        Dish d = dish("Pollo");
        reload();
        dishes.save(new Dish(d.id(), "Pollo 2", List.of(line(a, "100")), d.version()));
        reload();
        assertThat(jdbc.queryForObject("select count(*) from recipe_line where dish_id = ?", Integer.class, d.id())).isEqualTo(1);
    }

    @Test
    void recipeOnlyChangeBumpsDishVersionAndStaleVersionConflicts() {
        Dish d = dish("Pollo");
        reload();
        Dish edited = dishes.save(new Dish(d.id(), "Pollo", List.of(line(b, "80")), d.version()));
        assertThat(edited.version()).isGreaterThan(d.version());
        reload();
        assertThat(jdbc.queryForObject("select version from dish where dish_id = ?", Long.class, d.id()))
                .isEqualTo(edited.version());
        // a second edit based on the old version must not add another set of lines
        assertThatThrownBy(() -> dishes.save(new Dish(d.id(), "Pollo", List.of(line(a, "90")), d.version())))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    private List<MenuSlot> menus(long... dishIds) {
        return IntStream.range(0, dishIds.length).mapToObj(i -> new MenuSlot(i + 1, dishIds[i])).toList();
    }

    @Test
    void versionMenusKeepPositionOrder() {
        long[] ds = IntStream.range(0, 5).mapToLong(i -> dish("D" + i).id()).toArray();
        Pack p = packs.save(new Pack(null, 1, "Pack 1", true, null, null));
        List<MenuSlot> order = menus(ds[4], ds[2], ds[0], ds[3], ds[1]);
        PackVersion late = packs.addVersion(new PackVersion(null, p.id(), LocalDate.of(2026, 6, 1), order));
        PackVersion early = packs.addVersion(new PackVersion(null, p.id(), LocalDate.of(2026, 1, 1), menus(ds)));
        reload();
        List<PackVersion> found = packs.findVersions(p.id());
        assertThat(found).extracting(PackVersion::id).containsExactly(early.id(), late.id());
        assertThat(found.get(1).menus()).isEqualTo(order);
        assertThat(packs.findAllVersions()).hasSize(2);
        assertThat(jdbc.queryForObject("select count(*) from pack_version_menu where pack_version_id = ? and position = 5 and dish_id = ?",
                Integer.class, late.id(), ds[1])).isEqualTo(1);
    }

    @Test
    void dishUsageIgnoresDeletedPacks() {
        long[] ds = IntStream.range(0, 5).mapToLong(i -> dish("D" + i).id()).toArray();
        Pack p = packs.save(new Pack(null, 1, "Pack 1", true, new BigDecimal("10.00"), null));
        PackVersion v = packs.addVersion(new PackVersion(null, p.id(), LocalDate.of(2026, 1, 1), menus(ds)));
        reload();
        assertThat(packs.isDishUsed(ds[0])).isTrue();
        assertThat(packs.findById(p.id()).orElseThrow()).isEqualTo(p);

        packs.softDelete(p.id(), actor);
        reload();
        assertThat(packs.isDishUsed(ds[0])).isFalse();
        assertThat(packs.findById(p.id())).isEmpty();
        assertThat(packs.findVersions(p.id())).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from pack_version where pack_version_id = ? and deleted_at is not null and deleted_by = ?",
                Integer.class, v.id(), actor)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from pack_version_menu where pack_version_id = ? and deleted_at is not null and deleted_by = ?",
                Integer.class, v.id(), actor)).isEqualTo(5);
    }

    @Test
    void findAllByIdMatchesFilteringFindAllAndSkipsDeleted() {
        Pack a = packs.save(new Pack(null, 1, "Pack 1", true, null, null));
        Pack b = packs.save(new Pack(null, 2, "Pack 2", true, null, null));
        Pack c = packs.save(new Pack(null, 3, "Pack 3", true, null, null));
        packs.softDelete(c.id(), actor);
        reload();

        assertThat(packs.findAllById(List.of(c.id(), b.id(), 99_999L)))
                .isEqualTo(packs.findAll().stream().filter(p -> p.id().equals(b.id())).toList());
        assertThat(packs.findAllById(List.of(a.id(), b.id(), c.id()))).extracting(Pack::id)
                .containsExactlyInAnyOrder(a.id(), b.id());
        assertThat(packs.findAllById(List.of())).isEmpty();
    }

    @Test
    void maxNumberStartsAtZero() {
        assertThat(packs.maxNumber()).isZero();
        packs.save(new Pack(null, 3, "Pack 3", true, null, null));
        packs.save(new Pack(null, 7, "Pack 7", true, null, null));
        reload();
        assertThat(packs.maxNumber()).isEqualTo(7);
        assertThat(packs.findAll()).extracting(Pack::number).containsExactly(3, 7);
    }

    @Test
    void maxNumberCountsDeletedPacksSoNumbersAreNotReused() {
        packs.save(new Pack(null, 3, "Pack 3", true, null, null));
        long seven = packs.save(new Pack(null, 7, "Pack 7", true, null, null)).id();
        packs.softDelete(seven, actor);
        reload();
        assertThat(packs.maxNumber()).isEqualTo(7);
    }
}
