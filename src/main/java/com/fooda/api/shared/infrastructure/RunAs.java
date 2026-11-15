package com.fooda.api.shared.infrastructure;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/** Runs work as a given user (e.g. from jobs/bootstrap) so auditing records an author. */
public final class RunAs {

    private RunAs() {
    }

    public static void user(long userId, Runnable work) {
        SecurityContext previous = SecurityContextHolder.getContext();
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(String.valueOf(userId), null, List.of()));
        SecurityContextHolder.setContext(ctx);
        try {
            work.run();
        } finally {
            SecurityContextHolder.setContext(previous);
        }
    }
}
