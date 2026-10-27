package com.smartqueue.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "holidays",
        uniqueConstraints = @UniqueConstraint(name = "uk_holidays_business_date", columnNames = {"business_id", "holiday_date"}))
@Getter
@Setter
@NoArgsConstructor
public class Holiday extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false, foreignKey = @ForeignKey(name = "fk_holidays_business"))
    private Business business;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate date;

    @Column(length = 120)
    private String name;
}
