package com.smartqueue.scheduling;

import com.smartqueue.entity.*;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Pure, deterministic queue ordering (no I/O). See docs/SCHEDULING.md.
 *
 * Selection order for the next customer:
 *  1. EMERGENCY entries (oldest first)
 *  2. Starvation guard: anyone waiting >= maxWaitMinutes (longest wait first)
 *  3. Fairness: after N consecutive non-NORMAL calls, the oldest NORMAL entry
 *  4. Highest score = levelWeight + agingPointsPerMinute * minutesWaited (ties: oldest queueTime, then id)
 */
@Component
public class QueueScheduler {

    /** Priority level actually applied now (appointments outside their window behave like NORMAL). */
    public PriorityLevel effectiveLevel(QueueEntry e, QueuePolicy p, Instant now) {
        PriorityLevel base = e.getPriority();
        if (base != PriorityLevel.APPOINTMENT) {
            return base;
        }
        Appointment a = e.getAppointment();
        if (p.getAppointmentPolicy() == AppointmentPolicy.FIFO || a == null) {
            return PriorityLevel.NORMAL;
        }
        Instant windowStart = a.getStartAt().minus(Duration.ofMinutes(p.getAppointmentEarlyWindowMinutes()));
        Instant windowEnd = a.getStartAt().plus(Duration.ofMinutes(p.getAppointmentLateGraceMinutes()));
        if (now.isBefore(windowStart) || now.isAfter(windowEnd)) {
            return PriorityLevel.NORMAL;
        }
        return PriorityLevel.APPOINTMENT;
    }

    public double minutesWaited(QueueEntry e, Instant now) {
        return Math.max(0, Duration.between(e.getQueueTime(), now).toSeconds()) / 60.0;
    }

    public double score(QueueEntry e, QueuePolicy p, Instant now) {
        return p.weightFor(effectiveLevel(e, p, now)) + p.getAgingPointsPerMinute() * minutesWaited(e, now);
    }

    private Comparator<QueueEntry> byScoreDesc(QueuePolicy p, Instant now) {
        return Comparator.<QueueEntry>comparingDouble(e -> score(e, p, now)).reversed()
                .thenComparing(QueueEntry::getQueueTime)
                .thenComparing(e -> e.getId() == null ? 0L : e.getId());
    }

    private Comparator<QueueEntry> oldestFirst() {
        return Comparator.comparing(QueueEntry::getQueueTime)
                .thenComparing(e -> e.getId() == null ? 0L : e.getId());
    }

    public Optional<QueueEntry> pickNext(Collection<QueueEntry> waiting, QueuePolicy p, int consecutiveHighServed, Instant now) {
        if (waiting.isEmpty()) {
            return Optional.empty();
        }
        // 1. emergencies
        Optional<QueueEntry> emergency = waiting.stream()
                .filter(e -> effectiveLevel(e, p, now) == PriorityLevel.EMERGENCY).min(oldestFirst());
        if (emergency.isPresent()) {
            return emergency;
        }
        // 2. starvation guard
        if (p.getMaxWaitMinutes() > 0) {
            Optional<QueueEntry> starved = waiting.stream()
                    .filter(e -> minutesWaited(e, now) >= p.getMaxWaitMinutes()).min(oldestFirst());
            if (starved.isPresent()) {
                return starved;
            }
        }
        // 3. fairness for NORMAL customers
        if (p.getFairnessEveryN() > 0 && consecutiveHighServed >= p.getFairnessEveryN()) {
            Optional<QueueEntry> normal = waiting.stream()
                    .filter(e -> effectiveLevel(e, p, now) == PriorityLevel.NORMAL).min(oldestFirst());
            if (normal.isPresent()) {
                return normal;
            }
        }
        // 4. best score
        return waiting.stream().min(byScoreDesc(p, now));
    }

    /** Value of the consecutive-high-priority counter after serving an entry of the given effective level. */
    public int nextConsecutive(PriorityLevel servedLevel, int current, QueuePolicy p) {
        if (servedLevel == PriorityLevel.NORMAL) {
            return 0;
        }
        if (servedLevel == PriorityLevel.EMERGENCY && p.isEmergencyBypassesFairness()) {
            return current;
        }
        return current + 1;
    }

    /** Full predicted service order, obtained by repeatedly applying pickNext. O(n^2), capped for safety. */
    public List<QueueEntry> order(Collection<QueueEntry> waiting, QueuePolicy p, int consecutiveHighServed, Instant now) {
        List<QueueEntry> remaining = new ArrayList<>(waiting);
        List<QueueEntry> ordered = new ArrayList<>(remaining.size());
        int consecutive = consecutiveHighServed;
        int guard = 0;
        while (!remaining.isEmpty() && guard++ < 2000) {
            QueueEntry next = pickNext(remaining, p, consecutive, now).orElseThrow();
            consecutive = nextConsecutive(effectiveLevel(next, p, now), consecutive, p);
            ordered.add(next);
            remaining.remove(next);
        }
        remaining.sort(oldestFirst());
        ordered.addAll(remaining);
        return ordered;
    }
}
