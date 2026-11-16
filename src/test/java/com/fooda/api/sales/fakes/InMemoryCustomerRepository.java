package com.fooda.api.sales.fakes;

import com.fooda.api.core.application.port.out.CustomerRepository;
import com.fooda.api.core.domain.sales.Customer;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.*;

public class InMemoryCustomerRepository implements CustomerRepository {

    private final Map<Long, Customer> rows = new LinkedHashMap<>();
    private long seq;

    @Override
    public List<Customer> findAll() { return List.copyOf(rows.values()); }

    @Override
    public Optional<Customer> findById(long id) { return Optional.ofNullable(rows.get(id)); }

    @Override
    public Customer save(Customer c) {
        long id = c.id() == null ? ++seq : c.id();
        Customer old = rows.get(id);
        if (old != null && !Objects.equals(old.version(), c.version())) {
            throw new OptimisticLockingFailureException("stale customer " + id);
        }
        long version = old == null ? 0 : old.version() + 1;
        Customer saved = new Customer(id, c.firstName(), c.lastName(), c.phone(), c.address(), c.birthday(), c.notes(), version);
        rows.put(id, saved);
        return saved;
    }

    @Override
    public void softDelete(long id, long actorId) { rows.remove(id); }
}
