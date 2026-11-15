package com.fooda.api.user.application.port.in;

import com.fooda.api.shared.application.Page;
import com.fooda.api.user.domain.User;
import com.fooda.api.user.domain.UserId;

public interface ManageUsersUseCase {

    Page<User> list(int page, int size);

    User get(UserId id);

    User create(NewUser user);

    User update(UserId actor, UserId target, UserChanges changes);

    /** Creates the first admin when there are no users; false when users already exist. */
    boolean ensureAdmin(String email, String password, String fullName);
}
