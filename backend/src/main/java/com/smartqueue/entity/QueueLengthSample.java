package com.smartqueue.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "queue_length_samples", indexes = @Index(name = "idx_qls_business_time", columnList = "business_id, sampled_at"))
@Getter
@Setter
@NoArgsConstructor
public class QueueLengthSample {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "queue_id", nullable = false)
    private Long queueId;

    @Column(name = "sampled_at", nullable = false)
    private Instant sampledAt;

    @Column(name = "queue_length", nullable = false)
    private int queueLength;
}
