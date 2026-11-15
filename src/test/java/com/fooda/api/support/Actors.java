package com.fooda.api.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class Actors {

    private static final AtomicInteger SEQ = new AtomicInteger();

    private Actors() {
    }

    public static long insertUser(JdbcTemplate jdbc) {
        return jdbc.queryForObject(
                "insert into app_user (email, full_name, password_hash, role) values (?, 'Actor', 'h', 'ADMIN') returning user_id",
                Long.class, "actor" + SEQ.incrementAndGet() + "@test.com");
    }

    public static void actAs(long userId) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(String.valueOf(userId), null, List.of()));
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
