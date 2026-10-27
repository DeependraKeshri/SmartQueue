package com.smartqueue.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/** A live queue for one service. The row is locked (SELECT ... FOR UPDATE) for every state-changing operation. */
@Entity
@Table(name = "queues",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_queues_public_id", columnNames = "public_id"),
                @UniqueConstraint(name = "uk_queues_service", columnNames = "service_id"),
                @UniqueConstraint(name = "uk_queues_business_prefix", columnNames = {"business_id", "token_prefix"})},
        indexes = @Index(name = "idx_queues_business", columnList = "business_id"))
@Getter
@Setter
@NoArgsConstructor
public class ServiceQueue extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false, foreignKey = @ForeignKey(name = "fk_queues_business"))
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false, foreignKey = @ForeignKey(name = "fk_queues_service"))
    private BusinessService service;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private QueueStatus status = QueueStatus.ACTIVE;

    @Column(name = "token_prefix", nullable = false, length = 5)
    private String tokenPrefix;

    @Column(name = "last_token_number", nullable = false)
    private int lastTokenNumber = 0;

    /** Date (business timezone) the token sequence belongs to; the sequence resets daily. */
    @Column(name = "sequence_date")
    private LocalDate sequenceDate;

    @Column(name = "consecutive_high_priority_served", nullable = false)
    private int consecutiveHighPriorityServed = 0;
}
