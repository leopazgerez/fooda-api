package com.fooda.api.catalog.infrastructure.out.persistence;

import com.fooda.api.core.application.port.out.DishRepository;
import com.fooda.api.core.domain.catalog.Dish;
import com.fooda.api.core.domain.catalog.RecipeLine;
import com.fooda.api.core.domain.catalog.Unit;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Sort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository // translates the force-increment's OptimisticLockException into Spring's (→ 409)
public class DishPersistenceAdapter implements DishRepository {

    private final DishJpaRepository dishes;
    private final RecipeLineJpaRepository lines;
    private final Clock clock;
    private final AuditorAware<Long> auditor;
    private final EntityManager em;

    public DishPersistenceAdapter(DishJpaRepository dishes, RecipeLineJpaRepository lines, Clock clock,
                                  AuditorAware<Long> auditor, EntityManager em) {
        this.em = em;
        this.dishes = dishes;
        this.lines = lines;
        this.clock = clock;
        this.auditor = auditor;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Dish> findAll() {
        Map<Long, List<RecipeLine>> byDish = new LinkedHashMap<>();
        lines.findAllByOrderById().forEach(l -> byDish.computeIfAbsent(l.dishId, k -> new ArrayList<>()).add(toDomain(l)));
        return dishes.findAll(Sort.by("name")).stream().map(e -> toDomain(e, byDish.getOrDefault(e.id, List.of()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Dish> findById(long id) {
        return dishes.findById(id).map(e -> toDomain(e, recipeOf(id)));
    }

    @Override
    @Transactional
    public Dish save(Dish d) {
        DishJpaEntity e = new DishJpaEntity();
        e.id = d.id();
        e.name = d.name();
        e.setVersion(d.version());
        DishJpaEntity saved = dishes.saveAndFlush(e);
        if (d.id() == null || !sameRecipe(recipeOf(saved.id), d.recipe())) {
            if (d.id() != null) {
                // the lines live outside the dish row: bump its version so concurrent recipe edits conflict
                em.lock(saved, LockModeType.PESSIMISTIC_FORCE_INCREMENT);
                Long actor = auditor.getCurrentAuditor()
                        .orElseThrow(() -> new IllegalStateException("No hay usuario autenticado para auditar"));
                lines.softDeleteByDish(saved.id, actor, clock.instant());
            }
            for (RecipeLine l : d.recipe()) {
                RecipeLineJpaEntity r = new RecipeLineJpaEntity();
                r.dishId = saved.id;
                r.supplyId = l.supplyId();
                r.quantity = l.quantity();
                r.unit = l.unit().code();
                r.portions = l.portions();
                lines.saveAndFlush(r);
            }
        }
        return toDomain(saved, recipeOf(saved.id));
    }

    @Override
    @Transactional
    public void softDelete(long id, long actorId) {
        lines.softDeleteByDish(id, actorId, clock.instant());
        dishes.softDelete(id, actorId, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSupplyUsed(long supplyId) {
        return lines.existsLiveBySupply(supplyId);
    }

    private List<RecipeLine> recipeOf(long dishId) {
        return lines.findByDishIdOrderById(dishId).stream().map(DishPersistenceAdapter::toDomain).toList();
    }

    /** Field by field with compareTo: record equals is scale-sensitive and the DB returns column scale. */
    private static boolean sameRecipe(List<RecipeLine> a, List<RecipeLine> b) {
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            RecipeLine x = a.get(i);
            RecipeLine y = b.get(i);
            if (x.supplyId() != y.supplyId() || x.unit() != y.unit()
                    || x.quantity().compareTo(y.quantity()) != 0 || x.portions().compareTo(y.portions()) != 0) {
                return false;
            }
        }
        return true;
    }

    private static RecipeLine toDomain(RecipeLineJpaEntity l) {
        return new RecipeLine(l.supplyId, l.quantity, Unit.of(l.unit, "unit"), l.portions);
    }

    private static Dish toDomain(DishJpaEntity e, List<RecipeLine> recipe) {
        return new Dish(e.id, e.name, recipe, e.getVersion());
    }
}
