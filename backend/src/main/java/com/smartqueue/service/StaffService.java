package com.smartqueue.service;

import com.smartqueue.audit.AuditService;
import com.smartqueue.common.ApiException;
import com.smartqueue.dto.AuthDtos.*;
import com.smartqueue.dto.Mappers;
import com.smartqueue.entity.*;
import com.smartqueue.repository.*;
import com.smartqueue.security.UserPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/** Staff accounts of a business. Admins manage managers/staff/displays; managers manage staff/displays. */
@Service
public class StaffService {
    private static final Set<Role> BUSINESS_ROLES = EnumSet.of(Role.BUSINESS_ADMIN, Role.MANAGER, Role.STAFF, Role.DISPLAY);

    private final AppUserRepository users;
    private final BusinessRepository businesses;
    private final BusinessServiceRepository services;
    private final CounterRepository counters;
    private final PasswordEncoder encoder;
    private final AuditService audit;

    public StaffService(AppUserRepository users, BusinessRepository businesses, BusinessServiceRepository services,
                        CounterRepository counters, PasswordEncoder encoder, AuditService audit) {
        this.users = users;
        this.businesses = businesses;
        this.services = services;
        this.counters = counters;
        this.encoder = encoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<UserDto> list(Long businessId) {
        return users.findByBusinessIdOrderByFullNameAsc(businessId).stream().map(Mappers::user).toList();
    }

    @Transactional
    public UserDto create(UserPrincipal actor, CreateStaffRequest r) {
        requireCanManage(actor, r.role());
        String email = AuthService.normalizeEmail(r.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        AppUser u = new AppUser();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(r.password()));
        u.setFullName(r.fullName().trim());
        u.setPhone(r.phone() == null || r.phone().isBlank() ? null : r.phone().trim());
        u.setRole(r.role());
        u.setBusiness(businesses.getReferenceById(actor.getBusinessId()));
        assignServices(u, actor.getBusinessId(), r.serviceIds());
        users.save(u);
        audit.record(actor.getBusinessId(), "STAFF_CREATED", "USER", u.getId(), Map.of("role", u.getRole().name()));
        return Mappers.user(u);
    }

    @Transactional
    public UserDto update(UserPrincipal actor, Long id, UpdateStaffRequest r) {
        AppUser u = users.findByIdAndBusinessId(id, actor.getBusinessId()).orElseThrow(() -> ApiException.notFound("User not found"));
        if (u.getRole() == Role.BUSINESS_ADMIN && !u.getId().equals(actor.getId())) {
            throw ApiException.forbidden("Business administrators can only be changed by themselves");
        }
        requireCanManage(actor, u.getRole());
        Map<String, Object> changes = new LinkedHashMap<>();
        if (r.fullName() != null && !r.fullName().isBlank()) {
            u.setFullName(r.fullName().trim());
        }
        if (r.phone() != null) {
            u.setPhone(r.phone().isBlank() ? null : r.phone().trim());
        }
        if (r.role() != null && r.role() != u.getRole()) {
            if (u.getRole() == Role.BUSINESS_ADMIN || r.role() == Role.BUSINESS_ADMIN || !BUSINESS_ROLES.contains(r.role())) {
                throw ApiException.badRequest("Role change not permitted");
            }
            requireCanManage(actor, r.role());
            changes.put("role", r.role().name());
            u.setRole(r.role());
        }
        if (r.active() != null && r.active() != u.isActive()) {
            if (!r.active() && u.getId().equals(actor.getId())) {
                throw ApiException.badRequest("You cannot deactivate your own account");
            }
            if (!r.active()) {
                for (Counter c : counters.findByAssignedStaffId(u.getId())) {
                    c.setAssignedStaff(null);
                }
            }
            u.setActive(r.active());
            changes.put("active", r.active());
        }
        if (r.serviceIds() != null) {
            assignServices(u, actor.getBusinessId(), r.serviceIds());
            changes.put("services", r.serviceIds().size());
        }
        audit.record(actor.getBusinessId(), "STAFF_UPDATED", "USER", u.getId(), changes);
        return Mappers.user(u);
    }

    private void assignServices(AppUser u, Long businessId, List<Long> ids) {
        u.getAssignedServices().clear();
        if (ids == null) {
            return;
        }
        for (Long sid : ids) {
            u.getAssignedServices().add(services.findByIdAndBusinessIdAndDeletedFalse(sid, businessId)
                    .orElseThrow(() -> ApiException.badRequest("Service " + sid + " not found in this business")));
        }
    }

    private void requireCanManage(UserPrincipal actor, Role target) {
        if (!BUSINESS_ROLES.contains(target) || target == Role.BUSINESS_ADMIN) {
            throw ApiException.badRequest("Role must be MANAGER, STAFF or DISPLAY");
        }
        if (actor.getRole() == Role.MANAGER && target == Role.MANAGER) {
            throw ApiException.forbidden("Only a business administrator can manage managers");
        }
        if (actor.getRole() != Role.BUSINESS_ADMIN && actor.getRole() != Role.MANAGER) {
            throw ApiException.forbidden("Not allowed");
        }
    }
}
