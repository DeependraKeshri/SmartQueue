package com.smartqueue.repository;

import com.smartqueue.entity.AppUser;
import com.smartqueue.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    Optional<AppUser> findByIdAndBusinessId(Long id, Long businessId);
    List<AppUser> findByBusinessIdOrderByFullNameAsc(Long businessId);
    List<AppUser> findByBusinessIdAndRoleInAndActiveTrue(Long businessId, Collection<Role> roles);
}
