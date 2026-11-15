package com.fooda.api.user.application.service;

import com.fooda.api.shared.application.Page;
import com.fooda.api.user.application.port.out.UserRepository;
import com.fooda.api.user.domain.User;
import com.fooda.api.user.domain.UserId;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public class InMemoryUserRepository implements UserRepository {

    private final Map<Long, User> byId = new TreeMap<>();
    private long next = 1;

    public InMemoryUserRepository() {
    }

    @Override
    public Optional<User> findById(UserId id) {
        return Optional.ofNullable(byId.get(id.value()));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return byId.values().stream().filter(u -> u.email().equals(email)).findFirst();
    }

    @Override
    public User save(User user) {
        User saved = user.id() == null
                ? new User(new UserId(next++), user.email(), user.fullName(), user.passwordHash(),
                        user.role(), user.emailVerified(), user.active())
                : user;
        byId.put(saved.id().value(), saved);
        return saved;
    }

    @Override
    public void lock(UserId id) {
    }

    @Override
    public boolean isEmpty() {
        return byId.isEmpty();
    }

    @Override
    public Page<User> list(int page, int size) {
        var all = byId.values().stream().toList();
        return new Page<>(all.stream().skip((long) page * size).limit(size).toList(), page, size, all.size());
    }
}
