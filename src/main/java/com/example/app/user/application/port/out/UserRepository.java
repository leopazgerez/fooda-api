package com.example.app.user.application.port.out;

import com.example.app.shared.application.Page;
import com.example.app.user.domain.User;
import com.example.app.user.domain.UserId;

import java.util.Optional;

public interface UserRepository {

    Optional<User> findById(UserId id);

    Optional<User> findByEmail(String normalizedEmail);

    User save(User user);

    /** Row lock until the surrounding transaction ends; a missing id is ignored. */
    void lock(UserId id);

    boolean isEmpty();

    /** Ordered by id; page starts at 0. */
    Page<User> list(int page, int size);
}
