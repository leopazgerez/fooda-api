package com.fooda.api.user.domain;

import com.fooda.api.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    @Test
    void normalizesEmail() {
        assertThat(new User(null, "  Ana@Mail.com ", "Ana", "h", Role.USER, false, true).email())
                .isEqualTo("ana@mail.com");
    }

    @Test
    void rejectsEmailWithoutAt() {
        assertThatThrownBy(() -> new User(null, "ana", "Ana", "h", Role.USER, false, true))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.field()).isEqualTo("email"));
    }

    @Test
    void rejectsNullRole() {
        assertThatThrownBy(() -> new User(null, "a@b.c", "Ana", "h", null, false, true))
                .isInstanceOfSatisfying(ValidationException.class, e -> {
                    assertThat(e.field()).isEqualTo("role");
                    assertThat(e.getMessage()).isEqualTo("El rol es obligatorio");
                });
    }

    @Test
    void toStringHidesHash() {
        assertThat(new User(null, "a@b.c", "Ana", "h4sh", Role.USER, false, true).toString()).doesNotContain("h4sh");
    }
}
