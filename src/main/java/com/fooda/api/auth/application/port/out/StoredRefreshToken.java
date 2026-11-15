package com.fooda.api.auth.application.port.out;

import com.fooda.api.user.domain.UserId;

public record StoredRefreshToken(long id, UserId userId) {
}
