package com.fooda.api.user.application.port.in;

import com.fooda.api.user.domain.User;
import com.fooda.api.user.domain.UserId;

public interface ProfileUseCase {

    User get(UserId id);

    User rename(UserId id, String fullName);
}
