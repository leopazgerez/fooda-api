package com.example.app.auth.application.port.out;

import com.example.app.user.domain.User;

public interface AccessTokenIssuer {

    IssuedToken issue(User user);
}
