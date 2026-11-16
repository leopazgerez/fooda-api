package com.fooda.api.user.infrastructure.out.persistence;

import com.fooda.api.TestcontainersConfiguration;
import com.fooda.api.config.JpaAuditingConfig;
import com.fooda.api.config.TimeConfig;
import com.fooda.api.support.Actors;
import com.fooda.api.support.Db;
import com.fooda.api.user.domain.Role;
import com.fooda.api.user.domain.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, UserPersistenceAdapter.class, JpaAuditingConfig.class, TimeConfig.class})
class UserAuditTest {

    @Autowired UserPersistenceAdapter users;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        Db.reset(jdbc);
    }

    @AfterEach
    void clear() {
        Actors.clear();
    }

    @Test
    void savingWhileActingRecordsCreatorAndUpdater() {
        long admin = Actors.insertUser(jdbc);
        Actors.actAs(admin);
        User saved = users.save(new User(null, "a@b.com", "Ana", "h", Role.USER, true, true));
        Map<String, Object> row = jdbc.queryForMap("select created_by, updated_by, created_at, updated_at from app_user where user_id = ?", saved.id().value());
        assertThat(row.get("created_by")).isEqualTo(admin);
        assertThat(row.get("updated_by")).isEqualTo(admin);
        assertThat(row.get("created_at")).isNotNull();
    }

    @Test
    void updateKeepsCreatorAndChangesUpdater() {
        long a = Actors.insertUser(jdbc), b = Actors.insertUser(jdbc);
        Actors.actAs(a);
        User saved = users.save(new User(null, "c@b.com", "Ana", "h", Role.USER, true, true));
        Actors.actAs(b);
        users.save(saved.withFullName("Ana María"));
        Map<String, Object> row = jdbc.queryForMap("select created_by, updated_by from app_user where user_id = ?", saved.id().value());
        assertThat(row.get("created_by")).isEqualTo(a);
        assertThat(row.get("updated_by")).isEqualTo(b);
    }

    @Test
    void anonymousSaveLeavesAuthorNull() {
        User saved = users.save(new User(null, "d@b.com", "Ana", "h", Role.USER, true, true));
        assertThat(jdbc.queryForObject("select created_by from app_user where user_id = ?", Long.class, saved.id().value())).isNull();
    }
}
