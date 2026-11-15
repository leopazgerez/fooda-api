package com.fooda.api.user.application.service;

import com.fooda.api.user.application.port.out.UserSessions;
import com.fooda.api.user.domain.UserId;

import java.util.ArrayList;
import java.util.List;

public class RecordingSessions implements UserSessions {

    public final List<UserId> revoked = new ArrayList<>();

    public RecordingSessions() {
    }

    @Override
    public void revokeAll(UserId userId) {
        revoked.add(userId);
    }
}
