package com.smartqueue.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/** Pre-aggregated per-day business statistics (rolled up once a day is complete). */
@Entity
@Table(name = "daily_stats",
        uniqueConstraints = @UniqueConstraint(name = "uk_daily_stats", columnNames = {"business_id", "stat_date"}))
@Getter
@Setter
@NoArgsConstructor
public class DailyStat extends BaseEntity {

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "total_customers", nullable = false)
    private int totalCustomers;

    @Column(nullable = false)
    private int completed;

    @Column(nullable = false)
    private int cancelled;

    @Column(name = "no_shows", nullable = false)
    private int noShows;

    @Column(name = "sum_wait_seconds", nullable = false)
    private long sumWaitSeconds;

    @Column(name = "max_wait_seconds", nullable = false)
    private long maxWaitSeconds;

    @Column(name = "median_wait_seconds", nullable = false)
    private long medianWaitSeconds;

    @Column(name = "sum_service_seconds", nullable = false)
    private long sumServiceSeconds;

    @Column(name = "appointments_booked", nullable = false)
    private int appointmentsBooked;

    @Column(name = "appointments_completed", nullable = false)
    private int appointmentsCompleted;

    @Column(name = "appointments_no_show", nullable = false)
    private int appointmentsNoShow;

    @Column(name = "appointments_cancelled", nullable = false)
    private int appointmentsCancelled;
}
