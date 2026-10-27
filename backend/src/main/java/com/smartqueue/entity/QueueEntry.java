package com.smartqueue.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One customer's place in a queue. Never deleted: status transitions preserve history. */
@Entity
@Table(name = "queue_entries",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_entries_public_id", columnNames = "public_id"),
                @UniqueConstraint(name = "uk_entries_token", columnNames = {"queue_id", "sequence_date", "token_number"}),
                @UniqueConstraint(name = "uk_entries_idem", columnNames = {"queue_id", "idempotency_key"})},
        indexes = {
                @Index(name = "idx_entries_queue_status", columnList = "queue_id, status"),
                @Index(name = "idx_entries_business_joined", columnList = "business_id, joined_at"),
                @Index(name = "idx_entries_customer", columnList = "customer_id"),
                @Index(name = "idx_entries_counter_status", columnList = "counter_id, status")})
@Getter
@Setter
@NoArgsConstructor
public class QueueEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "queue_id", nullable = false, foreignKey = @ForeignKey(name = "fk_entries_queue"))
    private ServiceQueue queue;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false, foreignKey = @ForeignKey(name = "fk_entries_business"))
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false, foreignKey = @ForeignKey(name = "fk_entries_service"))
    private BusinessService service;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", foreignKey = @ForeignKey(name = "fk_entries_customer"))
    private AppUser customer;

    @Column(name = "guest_name", length = 120)
    private String guestName;

    @Column(name = "guest_phone", length = 30)
    private String guestPhone;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Column(name = "token_number", nullable = false)
    private int tokenNumber;

    @Column(name = "token_label", nullable = false, length = 16)
    private String tokenLabel;

    @Column(name = "sequence_date", nullable = false)
    private LocalDate sequenceDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EntryStatus status = EntryStatus.WAITING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EntrySource source = EntrySource.VIRTUAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PriorityLevel priority = PriorityLevel.NORMAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", foreignKey = @ForeignKey(name = "fk_entries_appointment"))
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "counter_id", foreignKey = @ForeignKey(name = "fk_entries_counter"))
    private Counter counter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "served_by_id", foreignKey = @ForeignKey(name = "fk_entries_served_by"))
    private AppUser servedBy;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    /** Position-in-line clock: equals joinedAt unless the entry was moved to the end. */
    @Column(name = "queue_time", nullable = false)
    private Instant queueTime;

    @Column(name = "called_at")
    private Instant calledAt;

    @Column(name = "serving_started_at")
    private Instant servingStartedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "no_show_at")
    private Instant noShowAt;

    @Column(name = "skipped_at")
    private Instant skippedAt;

    @Column(name = "recall_count", nullable = false)
    private int recallCount = 0;

    @Column(name = "skip_count", nullable = false)
    private int skipCount = 0;

    @Column(name = "approaching_notified_at")
    private Instant approachingNotifiedAt;

    @Column(name = "no_show_warned_at")
    private Instant noShowWarnedAt;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(length = 255)
    private String note;

    public String displayName() {
        if (customer != null) {
            return customer.getFullName();
        }
        return guestName == null ? "Guest" : guestName;
    }
}
