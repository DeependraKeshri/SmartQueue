package com.smartqueue.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Web-push subscription (VAPID). Storage is ready; the sender is a pluggable port. */
@Entity
@Table(name = "push_subscriptions",
        uniqueConstraints = @UniqueConstraint(name = "uk_push_endpoint", columnNames = "endpoint"),
        indexes = @Index(name = "idx_push_user", columnList = "user_id"))
@Getter
@Setter
@NoArgsConstructor
public class PushSubscription extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 500)
    private String endpoint;

    @Column(length = 200)
    private String p256dh;

    @Column(name = "auth_key", length = 100)
    private String authKey;
}
