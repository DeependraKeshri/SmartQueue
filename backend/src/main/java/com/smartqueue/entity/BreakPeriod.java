package com.smartqueue.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.DayOfWeek;
import java.time.LocalTime;

/** Recurring break (e.g. lunch). dayOfWeek == null means every day. */
@Entity
@Table(name = "break_periods", indexes = @Index(name = "idx_breaks_business", columnList = "business_id"))
@Getter
@Setter
@NoArgsConstructor
public class BreakPeriod extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false, foreignKey = @ForeignKey(name = "fk_breaks_business"))
    private Business business;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", length = 12)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(length = 80)
    private String label;
}
