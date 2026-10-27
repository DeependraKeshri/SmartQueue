package com.smartqueue.security;

import com.smartqueue.common.ApiException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Access to the authenticated principal. Tenant id ALWAYS comes from here, never from request input. */
public final class CurrentUser {
    private CurrentUser() {}

    public static Optional<UserPrincipal> get() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof UserPrincipal p) {
            return Optional.of(p);
        }
        return Optional.empty();
    }

    public static UserPrincipal require() {
        return get().orElseThrow(() -> ApiException.unauthorized("Authentication required"));
    }

    public static Long requireBusinessId() {
        Long id = require().getBusinessId();
        if (id == null) {
            throw ApiException.forbidden("This account is not associated with a business");
        }
        return id;
    }
}
