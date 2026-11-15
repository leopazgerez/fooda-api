package com.fooda.api.auth.application.port.out;

import com.fooda.api.user.domain.User;

public interface AccessTokenIssuer {

    IssuedToken issue(User user);
}
