package com.smartqueue.security;

import com.smartqueue.entity.AppUser;
import com.smartqueue.repository.AppUserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/** Validates a JWT and loads the CURRENT user state from the database (role, active, tenant). */
@Component
public class TokenAuthenticator {
    private final JwtService jwtService;
    private final AppUserRepository users;

    public TokenAuthenticator(JwtService jwtService, AppUserRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Optional<UserPrincipal> authenticate(String token) {
        return jwtService.parseUserId(token)
                .flatMap(users::findById)
                .filter(AppUser::isActive)
                .filter(u -> u.getBusiness() == null || u.getBusiness().isActive())
                .map(UserPrincipal::from);
    }
}
