package com.fooda.api.user.domain;

/** What a role allows. Endpoints check these with {@code @PreAuthorize("hasAuthority('NAME')")}, never the role. */
public enum Permission {
    USERS_READ,
    USERS_WRITE,
    CATALOG_READ,
    CATALOG_WRITE,
    SETTINGS_WRITE,
    PRODUCTION_READ,
    PRODUCTION_WRITE,
    STOCK_READ,
    STOCK_WRITE,
    SALES_READ,
    SALES_WRITE,
    FINANCE_READ,
    FINANCE_WRITE
}
