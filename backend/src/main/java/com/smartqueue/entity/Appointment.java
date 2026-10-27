package com.smartqueue.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "appointments",
        uniqueConstraints = @UniqueConstraint(name = "uk_appointments_idem", columnNames = {"customer_id", "idempotency_key"}),
        indexes = {
                @Index(name = "idx_appointments_service_start", columnList = "service_id, start_at"),
                @Index(name = "idx_appointments_business_start", columnList = "business_id, start_at"),
                @Index(name = "idx_appointments_customer", columnList = "customer_id")})
@Getter
@Setter
@NoArgsConstructor
public class Appointment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false, foreignKey = @ForeignKey(name = "fk_appointments_business"))
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_id", nullable = false, foreignKey = @ForeignKey(name = "fk_appointments_service"))
    private BusinessService service;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_appointments_customer"))
    private AppUser customer;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AppointmentStatus status = AppointmentStatus.BOOKED;

    @Column(name = "idempotency_key", length = 64)
    private String idempotencyKey;

    @Column(length = 500)
    private String notes;

    @Column(name = "reschedule_count", nullable = false)
    private int rescheduleCount = 0;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;
}
