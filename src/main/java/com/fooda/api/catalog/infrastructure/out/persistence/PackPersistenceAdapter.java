package com.fooda.api.catalog.infrastructure.out.persistence;

import com.fooda.api.core.application.port.out.PackRepository;
import com.fooda.api.core.domain.catalog.MenuSlot;
import com.fooda.api.core.domain.catalog.Pack;
import com.fooda.api.core.domain.catalog.PackVersion;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class PackPersistenceAdapter implements PackRepository {

    private final PackJpaRepository packs;
    private final PackVersionJpaRepository versions;
    private final PackVersionMenuJpaRepository menus;
    private final Clock clock;

    public PackPersistenceAdapter(PackJpaRepository packs, PackVersionJpaRepository versions,
                                  PackVersionMenuJpaRepository menus, Clock clock) {
        this.packs = packs;
        this.versions = versions;
        this.menus = menus;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Pack> findAll() {
        return packs.findAll(Sort.by("number")).stream().map(PackPersistenceAdapter::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Pack> findById(long id) {
        return packs.findById(id).map(PackPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public Pack save(Pack p) {
        PackJpaEntity e = new PackJpaEntity();
        e.id = p.id();
        e.number = p.number();
        e.name = p.name();
        e.active = p.active();
        e.price = p.price();
        e.setVersion(p.version());
        return toDomain(packs.saveAndFlush(e));
    }

    @Override
    @Transactional(readOnly = true)
    public int maxNumber() {
        return packs.maxNumber();
    }

    @Override
    @Transactional
    public void softDelete(long id, long actorId) {
        List<Long> versionIds = versions.findIdsByPack(id);
        if (!versionIds.isEmpty()) {
            menus.softDeleteByVersions(versionIds, actorId, clock.instant());
        }
        versions.softDeleteByPack(id, actorId, clock.instant());
        packs.softDelete(id, actorId, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PackVersion> findAllVersions() {
        Map<Long, List<MenuSlot>> byVersion = new HashMap<>();
        menus.findAllByOrderByPackVersionIdAscPositionAsc()
                .forEach(m -> byVersion.computeIfAbsent(m.packVersionId, k -> new ArrayList<>()).add(toDomain(m)));
        return versions.findAllOrdered().stream().map(v -> toDomain(v, byVersion.getOrDefault(v.id, List.of()))).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PackVersion> findVersions(long packId) {
        return versions.findByPack(packId).stream().map(v -> toDomain(v, menusOf(v.id))).toList();
    }

    @Override
    @Transactional
    public PackVersion addVersion(PackVersion v) {
        PackVersionJpaEntity e = new PackVersionJpaEntity();
        e.packId = v.packId();
        e.validFrom = v.validFrom();
        PackVersionJpaEntity saved = versions.saveAndFlush(e);
        short position = 1;
        for (MenuSlot slot : v.menus()) {
            PackVersionMenuJpaEntity m = new PackVersionMenuJpaEntity();
            m.packVersionId = saved.id;
            m.position = position++;
            m.menuNumber = slot.number();
            m.dishId = slot.dishId();
            menus.saveAndFlush(m);
        }
        return toDomain(saved, menusOf(saved.id));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isDishUsed(long dishId) {
        return menus.existsLiveByDish(dishId);
    }

    private List<MenuSlot> menusOf(long versionId) {
        return menus.findByPackVersionIdOrderByPosition(versionId).stream().map(PackPersistenceAdapter::toDomain).toList();
    }

    private static MenuSlot toDomain(PackVersionMenuJpaEntity m) {
        return new MenuSlot(m.menuNumber, m.dishId);
    }

    private static PackVersion toDomain(PackVersionJpaEntity e, List<MenuSlot> slots) {
        return new PackVersion(e.id, e.packId, e.validFrom, slots);
    }

    private static Pack toDomain(PackJpaEntity e) {
        return new Pack(e.id, e.number, e.name, e.active, e.price, e.getVersion());
    }
}
