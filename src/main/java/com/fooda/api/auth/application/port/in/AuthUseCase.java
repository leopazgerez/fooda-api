package com.fooda.api.auth.application.port.in;

import com.fooda.api.user.domain.UserId;

public interface AuthUseCase {

    void register(String email, String password, String fullName);

    void verifyEmail(String token);

    void resendVerification(String email);

    Session login(String email, String password);

    Session refresh(String refreshToken);

    void logout(String refreshToken);

    void forgotPassword(String email);

    void resetPassword(String token, String newPassword);

    Session changePassword(UserId userId, String currentPassword, String newPassword);
}
