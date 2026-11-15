package com.example.app.user.domain;

import java.util.EnumSet;
import java.util.Set;

/** Each role and its permissions. A new role also goes in the CHECK of app_user.role (V1__identity.sql). */
public enum Role {
    ADMIN(EnumSet.allOf(Permission.class)),
    USER(EnumSet.noneOf(Permission.class));

    private final Set<Permission> permissions;

    Role(Set<Permission> permissions) {
        this.permissions = permissions;
    }

    public Set<Permission> permissions() {
        return Set.copyOf(permissions);
    }

    public boolean can(Permission permission) {
        return permissions.contains(permission);
    }
}
