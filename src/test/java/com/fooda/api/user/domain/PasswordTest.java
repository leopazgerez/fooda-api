package com.fooda.api.user.domain;

import com.fooda.api.shared.domain.ValidationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordTest {

    @Test
    void rejectsShort() {
        assertThatThrownBy(() -> Password.check("password", "1234567"))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.field()).isEqualTo("password"));
    }

    @Test
    void rejectsMultibyteOver72Bytes() {
        assertThatThrownBy(() -> Password.check("password", "ñ".repeat(40)))
                .isInstanceOfSatisfying(ValidationException.class, e -> assertThat(e.field()).isEqualTo("password"));
    }

    @Test
    void acceptsExactly72Bytes() {
        assertThatCode(() -> Password.check("password", "a".repeat(72))).doesNotThrowAnyException();
    }
}
