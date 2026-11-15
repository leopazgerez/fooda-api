package com.fooda.api.user.infrastructure.out.persistence;

import com.fooda.api.shared.application.Page;
import com.fooda.api.user.application.port.out.UserRepository;
import com.fooda.api.user.domain.User;
import com.fooda.api.user.domain.UserId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class UserPersistenceAdapter implements UserRepository {

    private final UserJpaRepository repository;

    public UserPersistenceAdapter(UserJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findById(UserId id) {
        return repository.findById(id.value()).map(UserPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String normalizedEmail) {
        return repository.findByEmail(normalizedEmail).map(UserPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public User save(User user) {
        return toDomain(repository.saveAndFlush(toEntity(user)));
    }

    @Override
    @Transactional
    public void lock(UserId id) {
        repository.lockById(id.value());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEmpty() {
        return repository.count() == 0;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<User> list(int page, int size) {
        var result = repository.findAll(PageRequest.of(page, size, Sort.by("id")));
        return new Page<>(result.map(UserPersistenceAdapter::toDomain).getContent(), page, size, result.getTotalElements());
    }

    private static User toDomain(UserJpaEntity e) {
        return new User(new UserId(e.id), e.email, e.fullName, e.passwordHash, e.role, e.emailVerified, e.active, e.getVersion());
    }

    private static UserJpaEntity toEntity(User u) {
        UserJpaEntity e = new UserJpaEntity();
        e.id = u.id() == null ? null : u.id().value();
        e.email = u.email();
        e.fullName = u.fullName();
        e.passwordHash = u.passwordHash();
        e.role = u.role();
        e.emailVerified = u.emailVerified();
        e.active = u.active();
        e.setVersion(u.version());
        return e;
    }
}
