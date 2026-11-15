package com.example.app.user.application.port.out;

import com.example.app.user.domain.UserId;

public interface UserSessions {

    void revokeAll(UserId userId);
}
