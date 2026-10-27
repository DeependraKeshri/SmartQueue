package com.smartqueue.entity;

import java.util.EnumSet;
import java.util.Set;

/** Lifecycle of a queue entry. History is never deleted; entries only change status. */
public enum EntryStatus {
    WAITING, CALLED, SERVING, COMPLETED, SKIPPED, NO_SHOW, CANCELLED;

    /** Statuses in which a customer is considered "in the queue" (blocks duplicate joins). */
    public static final Set<EntryStatus> OCCUPYING = EnumSet.of(WAITING, CALLED, SERVING);
    public static final Set<EntryStatus> AT_COUNTER = EnumSet.of(CALLED, SERVING);

    public boolean canTransitionTo(EntryStatus target) {
        return switch (this) {
            case WAITING -> target == CALLED || target == CANCELLED;
            case CALLED -> target == SERVING || target == COMPLETED || target == SKIPPED
                    || target == NO_SHOW || target == CANCELLED || target == WAITING;
            case SERVING -> target == COMPLETED || target == CANCELLED;
            case SKIPPED -> target == CALLED || target == WAITING || target == NO_SHOW || target == CANCELLED;
            default -> false;
        };
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == NO_SHOW || this == CANCELLED;
    }
}
