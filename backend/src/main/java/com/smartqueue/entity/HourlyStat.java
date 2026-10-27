package com.smartqueue.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "hourly_stats",
        uniqueConstraints = @UniqueConstraint(name = "uk_hourly_stats", columnNames = {"business_id", "stat_date", "hour_of_day"}))
@Getter
@Setter
@NoArgsConstructor
public class HourlyStat extends BaseEntity {

    @Column(name = "business_id", nullable = false)
    private Long businessId;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "hour_of_day", nullable = false)
    private int hourOfDay;

    @Column(nullable = false)
    private int joined;

    @Column(nullable = false)
    private int completed;
}
