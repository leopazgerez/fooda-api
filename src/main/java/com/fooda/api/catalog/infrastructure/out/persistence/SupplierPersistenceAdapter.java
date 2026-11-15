package com.fooda.api.catalog.infrastructure.out.persistence;

import com.fooda.api.core.application.port.out.SupplierRepository;
import com.fooda.api.core.domain.catalog.Supplier;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

@Component
public class SupplierPersistenceAdapter implements SupplierRepository {

    private final SupplierJpaRepository repository;
    private final Clock clock;

    public SupplierPersistenceAdapter(SupplierJpaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Supplier> findAll() {
        return repository.findAll(Sort.by("sortOrder", "name")).stream().map(SupplierPersistenceAdapter::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Supplier> findById(long id) {
        return repository.findById(id).map(SupplierPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public Supplier save(Supplier s) {
        return toDomain(repository.saveAndFlush(toEntity(s)));
    }

    @Override
    @Transactional
    public void softDelete(long id, long actorId) {
        repository.softDelete(id, actorId, clock.instant());
    }

    private static Supplier toDomain(SupplierJpaEntity e) {
        return new Supplier(e.id, e.name, e.purchaseDay, e.sortOrder, e.getVersion());
    }

    private static SupplierJpaEntity toEntity(Supplier s) {
        SupplierJpaEntity e = new SupplierJpaEntity();
        e.id = s.id();
        e.name = s.name();
        e.purchaseDay = s.purchaseDay();
        e.sortOrder = s.sortOrder();
        e.setVersion(s.version());
        return e;
    }
}
