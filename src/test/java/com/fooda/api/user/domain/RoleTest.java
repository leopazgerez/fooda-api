package com.fooda.api.user.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleTest {

    @Test
    void adminHasEveryBusinessPermission() {
        assertThat(Role.ADMIN.permissions())
                .contains(Permission.CATALOG_WRITE, Permission.SETTINGS_WRITE, Permission.FINANCE_WRITE);
    }

    @Test
    void userHasNoPermissions() {
        assertThat(Role.USER.permissions()).isEmpty();
    }
}
