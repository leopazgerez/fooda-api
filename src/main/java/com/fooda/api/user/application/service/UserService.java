package com.fooda.api.user.application.service;

import com.fooda.api.shared.application.Page;
import com.fooda.api.shared.application.exception.ResourceNotFoundException;
import com.fooda.api.shared.application.port.out.Transactions;
import com.fooda.api.shared.domain.ConflictException;
import com.fooda.api.shared.domain.ValidationException;
import com.fooda.api.user.application.port.in.ManageUsersUseCase;
import com.fooda.api.user.application.port.in.NewUser;
import com.fooda.api.user.application.port.in.ProfileUseCase;
import com.fooda.api.user.application.port.in.UserChanges;
import com.fooda.api.user.application.port.out.PasswordHasher;
import com.fooda.api.user.application.port.out.UserRepository;
import com.fooda.api.user.application.port.out.UserSessions;
import com.fooda.api.user.domain.Password;
import com.fooda.api.user.domain.Permission;
import com.fooda.api.user.domain.Role;
import com.fooda.api.user.domain.User;
import com.fooda.api.user.domain.UserId;

public class UserService implements ProfileUseCase, ManageUsersUseCase {

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final UserSessions sessions;
    private final Transactions tx;

    public UserService(UserRepository users, PasswordHasher hasher, UserSessions sessions, Transactions tx) {
        this.users = users;
        this.hasher = hasher;
        this.sessions = sessions;
        this.tx = tx;
    }

    @Override
    public User get(UserId id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("No se encontró el usuario"));
    }

    @Override
    public User rename(UserId id, String fullName) {
        return users.save(get(id).withFullName(fullName));
    }

    @Override
    public Page<User> list(int page, int size) {
        if (page < 0) {
            throw new ValidationException("page", "La página debe ser 0 o mayor");
        }
        if (size < 1 || size > 100) {
            throw new ValidationException("size", "El tamaño debe estar entre 1 y 100");
        }
        return users.list(page, size);
    }

    @Override
    public User create(NewUser u) {
        Password.check("password", u.password());
        // a duplicate email is rejected by the unique index (mapped to 409 on "email")
        return users.save(new User(null, u.email(), u.fullName(), hasher.hash(u.password()),
                u.role(), true, true));
    }

    @Override
    public User update(UserId actor, UserId target, UserChanges c) {
        if (actor.equals(target)
                && (Boolean.FALSE.equals(c.active()) || (c.role() != null && !c.role().can(Permission.USERS_WRITE)))) {
            throw new ConflictException("No podés desactivarte ni quitarte el rol de administrador");
        }
        return tx.get(() -> {
            // lock both rows in id order: two admins editing each other wait instead of both passing the check below
            users.lock(actor.value() < target.value() ? actor : target);
            users.lock(actor.value() < target.value() ? target : actor);
            User caller = get(actor);
            if (!caller.active() || !caller.role().can(Permission.USERS_WRITE)) {
                throw new ConflictException("Tu usuario ya no tiene permisos de administrador");
            }
            User user = get(target);
            if (c.fullName() != null) {
                user = user.withFullName(c.fullName());
            }
            if (c.role() != null) {
                user = user.withRole(c.role());
            }
            if (c.active() != null) {
                user = user.withActive(c.active());
            }
            User saved = users.save(user);
            if (Boolean.FALSE.equals(c.active())) {
                sessions.revokeAll(target);
            }
            return saved;
        });
    }

    @Override
    public boolean ensureAdmin(String email, String password, String fullName) {
        Password.check("password", password);
        return tx.get(() -> {
            if (!users.isEmpty()) {
                return false;
            }
            users.save(new User(null, email, fullName, hasher.hash(password), Role.ADMIN, true, true));
            return true;
        });
    }
}
