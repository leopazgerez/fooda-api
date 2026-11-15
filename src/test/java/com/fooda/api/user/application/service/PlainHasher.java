package com.fooda.api.user.application.service;

import com.fooda.api.user.application.port.out.PasswordHasher;

public class PlainHasher implements PasswordHasher {

    public PlainHasher() {
    }

    @Override
    public String hash(String raw) {
        return "plain:" + raw;
    }

    @Override
    public boolean matches(String raw, String hash) {
        return hash != null && hash.equals("plain:" + raw);
    }
}
