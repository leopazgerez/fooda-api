package com.example.app.user.application.service;

import com.example.app.user.application.port.out.UserSessions;
import com.example.app.user.domain.UserId;

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
