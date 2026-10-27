package com.smartqueue.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "notification_preferences",
        uniqueConstraints = @UniqueConstraint(name = "uk_notif_pref_user", columnNames = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class NotificationPreference extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "in_app_enabled", nullable = false)
    private boolean inAppEnabled = true;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled = true;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled = false;

    /** Comma separated NotificationType names the user has muted. */
    @Column(name = "muted_types", length = 500)
    private String mutedTypes = "";

    public Set<String> mutedSet() {
        if (mutedTypes == null || mutedTypes.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(mutedTypes.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }
}
