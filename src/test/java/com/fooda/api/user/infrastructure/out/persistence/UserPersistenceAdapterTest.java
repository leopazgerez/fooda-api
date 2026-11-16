package com.fooda.api.user.infrastructure.out.persistence;

import com.fooda.api.TestcontainersConfiguration;
import com.fooda.api.config.JpaAuditingConfig;
import com.fooda.api.config.TimeConfig;
import com.fooda.api.support.Db;
import com.fooda.api.shared.application.Page;
import com.fooda.api.user.domain.Role;
import com.fooda.api.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, UserPersistenceAdapter.class, JpaAuditingConfig.class, TimeConfig.class})
class UserPersistenceAdapterTest {

    @Autowired UserPersistenceAdapter users;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        Db.reset(jdbc);
    }

    private User user(String email) {
        return new User(null, email, "Ana Pérez", "hash", Role.ADMIN, true, true);
    }

    @Test
    void saveAndFindByEmail() {
        User saved = users.save(user("ana@mail.com"));
        assertThat(saved.id()).isNotNull();
        User found = users.findByEmail("ana@mail.com").orElseThrow();
        assertThat(found).isEqualTo(saved);
        assertThat(found.fullName()).isEqualTo("Ana Pérez");
        assertThat(found.role()).isEqualTo(Role.ADMIN);
        assertThat(found.emailVerified()).isTrue();
        assertThat(users.findById(saved.id())).contains(saved);
    }

    @Test
    void duplicateEmailDifferentCaseViolatesIndex() {
        users.save(user("ana@mail.com"));
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO app_user (email, full_name, password_hash, role) VALUES ('ANA@mail.com', 'X', 'h', 'USER')"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void staleSnapshotCannotOverwriteNewerData() {
        User stale = users.save(user("ana@mail.com"));
        users.save(stale.withActive(false));
        assertThatThrownBy(() -> users.save(stale.withFullName("Otra")))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(users.findById(stale.id()).orElseThrow().active()).isFalse();
    }

    @Test
    void listPagesById() {
        users.save(user("a@mail.com"));
        users.save(user("b@mail.com"));
        users.save(user("c@mail.com"));
        Page<User> page = users.list(1, 2);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).email()).isEqualTo("c@mail.com");
        assertThat(page.total()).isEqualTo(3);
    }

    @Test
    void isEmpty() {
        assertThat(users.isEmpty()).isTrue();
        users.save(user("a@mail.com"));
        assertThat(users.isEmpty()).isFalse();
    }
}
