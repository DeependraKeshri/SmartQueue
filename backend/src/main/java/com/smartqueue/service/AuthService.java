package com.smartqueue.service;

import com.smartqueue.audit.AuditService;
import com.smartqueue.common.ApiException;
import com.smartqueue.dto.AuthDtos.*;
import com.smartqueue.dto.Mappers;
import com.smartqueue.entity.*;
import com.smartqueue.repository.*;
import com.smartqueue.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AuthService {
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private final AppUserRepository users;
    private final BusinessRepository businesses;
    private final QueuePolicyRepository policies;
    private final BusinessHoursRepository hoursRepo;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditService audit;
    private final String dummyHash;

    public AuthService(AppUserRepository users, BusinessRepository businesses, QueuePolicyRepository policies,
                       BusinessHoursRepository hoursRepo, PasswordEncoder encoder, JwtService jwt, AuditService audit) {
        this.users = users;
        this.businesses = businesses;
        this.policies = policies;
        this.hoursRepo = hoursRepo;
        this.encoder = encoder;
        this.jwt = jwt;
        this.audit = audit;
        this.dummyHash = encoder.encode("dummy-password-for-timing-equalisation");
    }

    @Transactional
    public AuthResponse registerCustomer(RegisterCustomerRequest req) {
        String email = normalizeEmail(req.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        AppUser u = new AppUser();
        u.setEmail(email);
        u.setPasswordHash(encoder.encode(req.password()));
        u.setFullName(req.fullName().trim());
        u.setPhone(blankToNull(req.phone()));
        u.setRole(Role.CUSTOMER);
        users.save(u);
        audit.record(null, "CUSTOMER_REGISTERED", "USER", u.getId());
        return respond(u);
    }

    @Transactional
    public AuthResponse registerBusiness(RegisterBusinessRequest req) {
        String email = normalizeEmail(req.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw ApiException.conflict("An account with this email already exists");
        }
        String tz = req.timezone() == null || req.timezone().isBlank() ? "UTC" : req.timezone().trim();
        try {
            ZoneId.of(tz);
        } catch (Exception e) {
            throw ApiException.badRequest("Unknown timezone: " + tz);
        }
        Business b = new Business();
        b.setName(req.businessName().trim());
        b.setSlug(uniqueSlug(req.businessName()));
        b.setTimezone(tz);
        b.setTokenPrefix(req.tokenPrefix() == null ? "A" : req.tokenPrefix().toUpperCase());
        b.setContactEmail(email);
        b.setContactPhone(blankToNull(req.phone()));
        businesses.save(b);

        policies.save(QueuePolicy.defaults(b));
        for (DayOfWeek d : DayOfWeek.values()) {
            BusinessHours h = new BusinessHours();
            h.setBusiness(b);
            h.setDayOfWeek(d);
            boolean weekend = d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY;
            h.setClosed(weekend);
            h.setOpenTime(weekend ? null : LocalTime.of(9, 0));
            h.setCloseTime(weekend ? null : LocalTime.of(17, 0));
            hoursRepo.save(h);
        }
        AppUser admin = new AppUser();
        admin.setEmail(email);
        admin.setPasswordHash(encoder.encode(req.password()));
        admin.setFullName(req.adminFullName().trim());
        admin.setPhone(blankToNull(req.phone()));
        admin.setRole(Role.BUSINESS_ADMIN);
        admin.setBusiness(b);
        users.save(admin);
        audit.record(b.getId(), "BUSINESS_REGISTERED", "BUSINESS", b.getId(), Map.of("name", b.getName()));
        return respond(admin);
    }

    /** noRollbackFor: failed-attempt counters and lockouts must persist even though we throw. */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(LoginRequest req) {
        AppUser u = users.findByEmailIgnoreCase(normalizeEmail(req.email())).orElse(null);
        if (u == null) {
            encoder.matches(req.password(), dummyHash);   // equalise timing; do not reveal which emails exist
            throw ApiException.unauthorized("Invalid email or password");
        }
        Instant now = Instant.now();
        if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(now)) {
            throw ApiException.unauthorized("Account temporarily locked due to repeated failed logins. Try again later.");
        }
        if (!encoder.matches(req.password(), u.getPasswordHash())) {
            u.setFailedLoginAttempts(u.getFailedLoginAttempts() + 1);
            if (u.getFailedLoginAttempts() >= MAX_FAILED_ATTEMPTS) {
                u.setLockedUntil(now.plus(LOCK_DURATION));
                u.setFailedLoginAttempts(0);
            }
            throw ApiException.unauthorized("Invalid email or password");
        }
        if (!u.isActive()) {
            throw ApiException.forbidden("This account has been deactivated");
        }
        if (u.getBusiness() != null && !u.getBusiness().isActive()) {
            throw ApiException.forbidden("This business account is suspended");
        }
        u.setFailedLoginAttempts(0);
        u.setLockedUntil(null);
        u.setLastLoginAt(now);
        return respond(u);
    }

    @Transactional(readOnly = true)
    public UserDto me(Long userId) {
        return Mappers.user(users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Unknown user")));
    }

    @Transactional
    public UserDto updateProfile(Long userId, UpdateProfileRequest req) {
        AppUser u = users.findById(userId).orElseThrow(() -> ApiException.unauthorized("Unknown user"));
        u.setFullName(req.fullName().trim());
        u.setPhone(blankToNull(req.phone()));
        return Mappers.user(u);
    }

    private AuthResponse respond(AppUser u) {
        return new AuthResponse(jwt.generate(u), "Bearer", jwt.expirationSeconds(), Mappers.user(u));
    }

    private String uniqueSlug(String name) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        if (base.isBlank()) {
            base = "business";
        }
        if (base.length() > 80) {
            base = base.substring(0, 80);
        }
        String slug = base;
        while (businesses.existsBySlug(slug)) {
            slug = base + "-" + UUID.randomUUID().toString().substring(0, 6);
        }
        return slug;
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
