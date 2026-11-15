package com.fooda.api.catalog.infrastructure.out.persistence;

import com.fooda.api.core.application.port.out.ReserveFundRepository;
import com.fooda.api.core.domain.catalog.ReserveFund;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

@Component
public class ReserveFundPersistenceAdapter implements ReserveFundRepository {

    private final ReserveFundJpaRepository repository;
    private final Clock clock;

    public ReserveFundPersistenceAdapter(ReserveFundJpaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReserveFund> findAll() {
        return repository.findAll(Sort.by("name")).stream().map(ReserveFundPersistenceAdapter::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ReserveFund> findById(long id) {
        return repository.findById(id).map(ReserveFundPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public ReserveFund save(ReserveFund r) {
        return toDomain(repository.saveAndFlush(toEntity(r)));
    }

    @Override
    @Transactional
    public void softDelete(long id, long actorId) {
        repository.softDelete(id, actorId, clock.instant());
    }

    private static ReserveFund toDomain(ReserveFundJpaEntity e) {
        return new ReserveFund(e.id, e.name, e.percentage, e.getVersion());
    }

    private static ReserveFundJpaEntity toEntity(ReserveFund r) {
        ReserveFundJpaEntity e = new ReserveFundJpaEntity();
        e.id = r.id();
        e.name = r.name();
        e.percentage = r.percentage();
        e.setVersion(r.version());
        return e;
    }
}
