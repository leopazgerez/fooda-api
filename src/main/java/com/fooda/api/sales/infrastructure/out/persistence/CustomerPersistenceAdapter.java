package com.fooda.api.sales.infrastructure.out.persistence;

import com.fooda.api.core.application.port.out.CustomerRepository;
import com.fooda.api.core.domain.sales.Customer;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

@Component
public class CustomerPersistenceAdapter implements CustomerRepository {

    private final CustomerJpaRepository repository;
    private final Clock clock;

    public CustomerPersistenceAdapter(CustomerJpaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Customer> findAll() {
        return repository.findAll(Sort.by("lastName", "firstName", "id")).stream().map(CustomerPersistenceAdapter::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Customer> findById(long id) {
        return repository.findById(id).map(CustomerPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public Customer save(Customer c) {
        return toDomain(repository.saveAndFlush(toEntity(c)));
    }

    @Override
    @Transactional
    public void softDelete(long id, long actorId) {
        repository.softDelete(id, actorId, clock.instant());
    }

    private static Customer toDomain(CustomerJpaEntity e) {
        return new Customer(e.id, e.firstName, e.lastName, e.phone, e.address, e.birthday, e.notes, e.getVersion());
    }

    private static CustomerJpaEntity toEntity(Customer c) {
        CustomerJpaEntity e = new CustomerJpaEntity();
        e.id = c.id();
        e.firstName = c.firstName();
        e.lastName = c.lastName();
        e.phone = c.phone();
        e.address = c.address();
        e.birthday = c.birthday();
        e.notes = c.notes();
        e.setVersion(c.version());
        return e;
    }
}
