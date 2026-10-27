package com.smartqueue.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZoneId;
import java.util.UUID;

/** Tenant root. Every tenant-owned table references a Business. */
@Entity
@Table(name = "businesses", uniqueConstraints = {
        @UniqueConstraint(name = "uk_businesses_slug", columnNames = "slug"),
        @UniqueConstraint(name = "uk_businesses_public_id", columnNames = "public_id")})
@Getter
@Setter
@NoArgsConstructor
public class Business extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 100)
    private String slug;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Column(length = 500)
    private String description;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(nullable = false, length = 64)
    private String timezone = "UTC";

    @Column(name = "token_prefix", nullable = false, length = 5)
    private String tokenPrefix = "A";

    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "display_mode", nullable = false, length = 32)
    private DisplayMode displayMode = DisplayMode.FIRST_NAME_INITIAL;

    @Column(name = "allow_guest_join", nullable = false)
    private boolean allowGuestJoin = true;

    @Column(name = "show_wait_on_display", nullable = false)
    private boolean showWaitOnDisplay = true;

    @Column(name = "in_app_notifications_enabled", nullable = false)
    private boolean inAppNotificationsEnabled = true;

    @Column(name = "email_notifications_enabled", nullable = false)
    private boolean emailNotificationsEnabled = true;

    @Column(name = "push_notifications_enabled", nullable = false)
    private boolean pushNotificationsEnabled = false;

    @Column(name = "auto_confirm_appointments", nullable = false)
    private boolean autoConfirmAppointments = true;

    @Column(name = "contact_email", length = 190)
    private String contactEmail;

    @Column(name = "contact_phone", length = 30)
    private String contactPhone;

    public ZoneId zone() {
        return ZoneId.of(timezone);
    }
}
