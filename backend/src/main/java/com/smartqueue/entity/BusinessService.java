package com.smartqueue.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** A service offered by a business (e.g. "General consultation"). Soft-deleted to preserve history. */
@Entity
@Table(name = "services",
        uniqueConstraints = @UniqueConstraint(name = "uk_services_public_id", columnNames = "public_id"),
        indexes = @Index(name = "idx_services_business", columnList = "business_id, deleted"))
@Getter
@Setter
@NoArgsConstructor
public class BusinessService extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false, foreignKey = @ForeignKey(name = "fk_services_business"))
    private Business business;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "estimated_duration_minutes", nullable = false)
    private int estimatedDurationMinutes = 10;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "queue_enabled", nullable = false)
    private boolean queueEnabled = true;

    @Column(name = "appointments_enabled", nullable = false)
    private boolean appointmentsEnabled = false;

    /** If true staff may assign PRIORITY / EMERGENCY levels to entries of this service. */
    @Column(name = "priority_allowed", nullable = false)
    private boolean priorityAllowed = true;

    @Column(name = "slot_interval_minutes", nullable = false)
    private int slotIntervalMinutes = 15;

    @Column(name = "max_advance_booking_days", nullable = false)
    private int maxAdvanceBookingDays = 30;

    @Column(name = "min_lead_minutes", nullable = false)
    private int minLeadMinutes = 0;

    @Column(name = "token_prefix", length = 5)
    private String tokenPrefix;

    @Column(nullable = false)
    private boolean deleted = false;
}
