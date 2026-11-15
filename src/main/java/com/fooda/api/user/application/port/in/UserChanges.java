package com.fooda.api.user.application.port.in;

import com.fooda.api.user.domain.Role;

/** A null field is left unchanged. */
public record UserChanges(String fullName, Role role, Boolean active) {
}
