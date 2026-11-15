package com.fooda.api.catalog.infrastructure.out.persistence;

import com.fooda.api.core.application.port.out.BranchRepository;
import com.fooda.api.core.domain.catalog.Branch;
import com.fooda.api.core.domain.catalog.Shipping;
import com.fooda.api.core.domain.catalog.ShippingType;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

@Component
public class BranchPersistenceAdapter implements BranchRepository {

    private final BranchJpaRepository repository;
    private final Clock clock;

    public BranchPersistenceAdapter(BranchJpaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Branch> findAll() {
        return repository.findAll(Sort.by("name")).stream().map(BranchPersistenceAdapter::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Branch> findById(long id) {
        return repository.findById(id).map(BranchPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Branch> findKitchen() {
        return repository.findFirstByKitchenTrue().map(BranchPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public Branch save(Branch b) {
        return toDomain(repository.saveAndFlush(toEntity(b)));
    }

    @Override
    @Transactional
    public void softDelete(long id, long actorId) {
        repository.softDelete(id, actorId, clock.instant());
    }

    private static Branch toDomain(BranchJpaEntity e) {
        return new Branch(e.id, e.name, e.address, e.capacity, e.stockThreshold, e.freeSlotsThreshold,
                new Shipping(ShippingType.of(e.shippingType, "shipping"), e.shippingValue),
                e.affectsPrice, e.kitchen, e.getVersion());
    }

    private static BranchJpaEntity toEntity(Branch b) {
        BranchJpaEntity e = new BranchJpaEntity();
        e.id = b.id();
        e.name = b.name();
        e.address = b.address();
        e.capacity = b.capacity();
        e.stockThreshold = b.stockThreshold();
        e.freeSlotsThreshold = b.freeSlotsThreshold();
        e.shippingType = b.shipping().type().code();
        e.shippingValue = b.shipping().value();
        e.affectsPrice = b.affectsPrice();
        e.kitchen = b.kitchen();
        e.setVersion(b.version());
        return e;
    }
}
