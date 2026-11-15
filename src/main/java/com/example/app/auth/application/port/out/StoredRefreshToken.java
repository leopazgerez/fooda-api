package com.example.app.auth.application.port.out;

import com.example.app.user.domain.UserId;

public record StoredRefreshToken(long id, UserId userId) {
}
