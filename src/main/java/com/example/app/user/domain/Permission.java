package com.example.app.user.domain;

/** What a role allows. Endpoints check these with {@code @PreAuthorize("hasAuthority('NAME')")}, never the role. */
public enum Permission {
    USERS_READ,
    USERS_WRITE
}
