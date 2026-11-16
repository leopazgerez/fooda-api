package com.fooda.api.core.domain.stock;

public enum BranchAlert {
    LOW_STOCK("low_stock"), FREE_SLOTS("free_slots");

    private final String code;

    BranchAlert(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
