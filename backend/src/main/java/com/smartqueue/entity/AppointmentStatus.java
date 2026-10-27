package com.smartqueue.entity;

import java.util.EnumSet;
import java.util.Set;

public enum AppointmentStatus {
    BOOKED, CONFIRMED, CHECKED_IN, COMPLETED, CANCELLED, NO_SHOW;

    /** Statuses that consume slot capacity. */
    public static final Set<AppointmentStatus> BLOCKING = EnumSet.of(BOOKED, CONFIRMED, CHECKED_IN);
    public static final Set<AppointmentStatus> PENDING = EnumSet.of(BOOKED, CONFIRMED);
}
