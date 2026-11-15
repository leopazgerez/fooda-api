package com.fooda.api.user.application.service;

import com.fooda.api.shared.DirectTransactions;
import com.fooda.api.shared.application.exception.ResourceNotFoundException;
import com.fooda.api.shared.domain.ConflictException;
import com.fooda.api.shared.domain.ValidationException;
import com.fooda.api.user.application.port.in.NewUser;
import com.fooda.api.user.application.port.in.UserChanges;
import com.fooda.api.user.domain.Role;
import com.fooda.api.user.domain.User;
import com.fooda.api.user.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserServiceTest {

    InMemoryUserRepository repo;
    RecordingSessions sessions;
    UserService service;
    User admin;

    @BeforeEach
    void setUp() {
        repo = new InMemoryUserRepository();
        sessions = new RecordingSessions();
        service = new UserService(repo, new PlainHasher(), sessions, new DirectTransactions());
        admin = repo.save(new User(null, "a@x.com", "Admin", "plain:x", Role.ADMIN, true, true));
    }

    User other() {
        return service.create(new NewUser("b@x.com", "B", Role.USER, "secret123"));
    }

    @Test
    void getUnknownIs404() {
        assertThatThrownBy(() -> service.get(new UserId(99))).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void renameTrims() {
        assertThat(service.rename(admin.id(), "  Ana B ").fullName()).isEqualTo("Ana B");
    }

    @Test
    void createIsVerifiedAndHashed() {
        User u = other();
        assertThat(u.emailVerified()).isTrue();
        assertThat(u.active()).isTrue();
        assertThat(u.passwordHash()).isEqualTo("plain:secret123");
    }

    @Test
    void createChecksPassword() {
        assertThatThrownBy(() -> service.create(new NewUser("c@x.com", "C", Role.USER, "corta")))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.field()).isEqualTo("password"));
    }

    @Test
    void deactivateRevokesSessions() {
        User t = other();
        User saved = service.update(admin.id(), t.id(), new UserChanges(null, null, false));
        assertThat(saved.active()).isFalse();
        assertThat(sessions.revoked).contains(t.id());
    }

    @Test
    void adminCannotDeactivateSelf() {
        assertThatThrownBy(() -> service.update(admin.id(), admin.id(), new UserChanges(null, null, false)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("No podés desactivarte ni quitarte el rol de administrador");
    }

    @Test
    void adminCannotDemoteSelf() {
        assertThatThrownBy(() -> service.update(admin.id(), admin.id(), new UserChanges(null, Role.USER, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("No podés desactivarte ni quitarte el rol de administrador");
    }

    @Test
    void adminChangesAnotherUsersRole() {
        User t = other();
        assertThat(service.update(admin.id(), t.id(), new UserChanges(null, Role.ADMIN, null)).role())
                .isEqualTo(Role.ADMIN);
    }

    @Test
    void deactivatedAdminCannotReactivateSelf() {
        repo.save(admin.withActive(false));
        assertThatThrownBy(() -> service.update(admin.id(), admin.id(), new UserChanges(null, null, true)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Tu usuario ya no tiene permisos de administrador");
        assertThat(repo.findById(admin.id()).orElseThrow().active()).isFalse();
    }

    @Test
    void demotedAdminCannotRePromoteSelf() {
        repo.save(admin.withRole(Role.USER));
        assertThatThrownBy(() -> service.update(admin.id(), admin.id(), new UserChanges(null, Role.ADMIN, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Tu usuario ya no tiene permisos de administrador");
        assertThat(repo.findById(admin.id()).orElseThrow().role()).isEqualTo(Role.USER);
    }

    @Test
    void listDelegatesPaging() {
        other();
        assertThat(service.list(0, 10).total()).isEqualTo(2);
        assertThatThrownBy(() -> service.list(0, 101))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.field()).isEqualTo("size"));
        assertThatThrownBy(() -> service.list(0, 0)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.list(-1, 10))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.field()).isEqualTo("page"));
    }

    @Test
    void ensureAdminOnlyWhenEmpty() {
        var fresh = new UserService(new InMemoryUserRepository(), new PlainHasher(), sessions, new DirectTransactions());
        assertThat(fresh.ensureAdmin("root@x.com", "secret123", "Root")).isTrue();
        assertThat(fresh.ensureAdmin("two@x.com", "secret123", "Two")).isFalse();
        User root = fresh.list(0, 10).items().get(0);
        assertThat(root.role()).isEqualTo(Role.ADMIN);
        assertThat(root.emailVerified()).isTrue();
    }
}
