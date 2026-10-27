package com.smartqueue.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-business configurable queue scheduling rules (Phase 9). One row per business.
 * See docs/SCHEDULING.md for the algorithm.
 */
@Entity
@Table(name = "queue_policies",
        uniqueConstraints = @UniqueConstraint(name = "uk_policies_business", columnNames = "business_id"))
@Getter
@Setter
@NoArgsConstructor
public class QueuePolicy extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false, foreignKey = @ForeignKey(name = "fk_policies_business"))
    private Business business;

    @Column(name = "emergency_weight", nullable = false)
    private int emergencyWeight = 1000;

    @Column(name = "appointment_weight", nullable = false)
    private int appointmentWeight = 500;

    @Column(name = "priority_weight", nullable = false)
    private int priorityWeight = 200;

    @Column(name = "normal_weight", nullable = false)
    private int normalWeight = 0;

    /** Score points gained per minute waited (aging). */
    @Column(name = "aging_points_per_minute", nullable = false)
    private double agingPointsPerMinute = 2.0;

    /** Anyone waiting at least this long is served before score ordering (starvation guard). 0 = off. */
    @Column(name = "max_wait_minutes", nullable = false)
    private int maxWaitMinutes = 60;

    /** After N consecutive non-NORMAL calls, serve the oldest NORMAL customer. 0 = off. */
    @Column(name = "fairness_every_n", nullable = false)
    private int fairnessEveryN = 4;

    @Column(name = "emergency_bypasses_fairness", nullable = false)
    private boolean emergencyBypassesFairness = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "appointment_policy", nullable = false, length = 32)
    private AppointmentPolicy appointmentPolicy = AppointmentPolicy.APPOINTMENT_PRIORITY;

    @Column(name = "appointment_early_window_minutes", nullable = false)
    private int appointmentEarlyWindowMinutes = 15;

    @Column(name = "appointment_late_grace_minutes", nullable = false)
    private int appointmentLateGraceMinutes = 10;

    @Column(name = "no_show_grace_minutes", nullable = false)
    private int noShowGraceMinutes = 5;

    @Column(name = "auto_no_show", nullable = false)
    private boolean autoNoShow = true;

    @Column(name = "max_recalls", nullable = false)
    private int maxRecalls = 2;

    @Column(name = "approaching_threshold", nullable = false)
    private int approachingThreshold = 3;

    public static QueuePolicy defaults(Business business) {
        QueuePolicy p = new QueuePolicy();
        p.setBusiness(business);
        return p;
    }

    public int weightFor(PriorityLevel level) {
        return switch (level) {
            case EMERGENCY -> emergencyWeight;
            case APPOINTMENT -> appointmentWeight;
            case PRIORITY -> priorityWeight;
            case NORMAL -> normalWeight;
        };
    }
}
