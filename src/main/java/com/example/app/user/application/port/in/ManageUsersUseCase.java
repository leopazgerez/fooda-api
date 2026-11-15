package com.example.app.user.application.port.in;

import com.example.app.shared.application.Page;
import com.example.app.user.domain.User;
import com.example.app.user.domain.UserId;

public interface ManageUsersUseCase {

    Page<User> list(int page, int size);

    User get(UserId id);

    User create(NewUser user);

    User update(UserId actor, UserId target, UserChanges changes);

    /** Creates the first admin when there are no users; false when users already exist. */
    boolean ensureAdmin(String email, String password, String fullName);
}
