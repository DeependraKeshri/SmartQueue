package com.smartqueue.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Historical service duration, written once when an entry is completed. Feeds ETA, analytics and demand models. */
@Entity
@Table(name = "service_duration_records",
        uniqueConstraints = @UniqueConstraint(name = "uk_durations_entry", columnNames = "entry_id"),
        indexes = {
                @Index(name = "idx_durations_service_completed", columnList = "service_id, completed_at"),
                @Index(name = "idx_durations_business_completed", columnList = "business_id, completed_at")})
@Getter
@Setter
@NoArgsConstructor
public class ServiceDurationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "service_id", nullable = false)
    private Long serviceId;

    @Column(name = "queue_id", nullable = false)
    private Long queueId;

    @Column(name = "entry_id", nullable = false)
    private Long entryId;

    @Column(name = "counter_id")
    private Long counterId;

    @Column(name = "staff_id")
    private Long staffId;

    @Column(name = "wait_seconds", nullable = false)
    private long waitSeconds;

    @Column(name = "duration_seconds", nullable = false)
    private long durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PriorityLevel priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EntrySource source;

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;
}
