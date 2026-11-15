package com.example.app.user.application.port.in;

import com.example.app.user.domain.User;
import com.example.app.user.domain.UserId;

public interface ProfileUseCase {

    User get(UserId id);

    User rename(UserId id, String fullName);
}
