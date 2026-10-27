package com.smartqueue.eta;

import java.time.Instant;
import java.util.List;

/** Everything the estimator needs, decoupled from persistence. */
public record EtaContext(Instant now,
                         List<Waiting> orderedWaiting,
                         List<InProgress> inProgress,
                         int openCounters,
                         double meanServiceSeconds,
                         double stdDevServiceSeconds,
                         long sampleCount,
                         boolean businessOpen,
                         Instant closingTime) {

    /** appointmentStart is non-null for appointment check-ins that must not be served before their slot. */
    public record Waiting(Long entryId, Instant appointmentStart) {}

    public record InProgress(Instant startedAt) {}
}
