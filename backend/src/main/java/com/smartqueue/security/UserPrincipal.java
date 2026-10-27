package com.smartqueue.security;

import com.smartqueue.entity.AppUser;
import com.smartqueue.entity.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/** Authenticated identity. getUsername() is the user id so STOMP /user destinations resolve by id. */
public class UserPrincipal implements UserDetails {
    private final Long id;
    private final String email;
    private final String fullName;
    private final Role role;
    private final Long businessId;

    public UserPrincipal(Long id, String email, String fullName, Role role, Long businessId) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.role = role;
        this.businessId = businessId;
    }

    public static UserPrincipal from(AppUser u) {
        return new UserPrincipal(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.businessId());
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public Role getRole() { return role; }
    public Long getBusinessId() { return businessId; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
    @Override public String getPassword() { return null; }
    @Override public String getUsername() { return String.valueOf(id); }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
