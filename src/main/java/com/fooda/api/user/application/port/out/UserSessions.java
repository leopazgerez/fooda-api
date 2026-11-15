package com.fooda.api.user.application.port.out;

import com.fooda.api.user.domain.UserId;

public interface UserSessions {

    void revokeAll(UserId userId);
}
