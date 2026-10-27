package com.smartqueue.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "counters",
        uniqueConstraints = @UniqueConstraint(name = "uk_counters_business_name", columnNames = {"business_id", "name"}),
        indexes = {@Index(name = "idx_counters_business", columnList = "business_id"),
                @Index(name = "idx_counters_staff", columnList = "assigned_staff_id")})
@Getter
@Setter
@NoArgsConstructor
public class Counter extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false, foreignKey = @ForeignKey(name = "fk_counters_business"))
    private Business business;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CounterStatus status = CounterStatus.CLOSED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_staff_id", foreignKey = @ForeignKey(name = "fk_counters_staff"))
    private AppUser assignedStaff;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "counter_services",
            joinColumns = @JoinColumn(name = "counter_id"),
            inverseJoinColumns = @JoinColumn(name = "service_id"))
    private Set<BusinessService> services = new HashSet<>();
}
