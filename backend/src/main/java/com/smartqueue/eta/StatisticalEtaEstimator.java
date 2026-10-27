package com.smartqueue.eta;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Counter-simulation estimator.
 *  - Each open counter has a "free at" time (remaining time of its current customer, else now).
 *  - Waiting customers (already in predicted service order, so priorities are respected) are assigned greedily to the
 *    earliest-free counter; each uses the blended mean service time.
 *  - Uncertainty grows with the number of services ahead per counter: spread = sd * sqrt(rounds).
 *  - Confidence reflects the number of historical samples and their variability.
 */
@Component
public class StatisticalEtaEstimator implements EtaEstimator {
    static final String METHOD = "STATISTICAL_V1";

    @Override
    public Map<Long, EtaResult> estimateAll(EtaContext ctx) {
        Map<Long, EtaResult> out = new LinkedHashMap<>();
        PriorityQueue<Double> free = initialCounters(ctx);
        int counters = free.size();
        int index = 0;
        for (EtaContext.Waiting w : ctx.orderedWaiting()) {
            out.put(w.entryId(), build(ctx, index, counters, free.peek(), w.appointmentStart()));
            double start = free.poll();
            double effectiveStart = start;
            if (w.appointmentStart() != null) {
                double offset = Duration.between(ctx.now(), w.appointmentStart()).toSeconds();
                effectiveStart = Math.max(start, offset);
            }
            free.add(effectiveStart + ctx.meanServiceSeconds());
            index++;
        }
        return out;
    }

    @Override
    public EtaResult estimateForNewJoin(EtaContext ctx) {
        PriorityQueue<Double> free = initialCounters(ctx);
        int counters = free.size();
        for (int i = 0; i < ctx.orderedWaiting().size(); i++) {
            free.add(free.poll() + ctx.meanServiceSeconds());
        }
        return build(ctx, ctx.orderedWaiting().size(), counters, free.peek(), null);
    }

    private PriorityQueue<Double> initialCounters(EtaContext ctx) {
        PriorityQueue<Double> free = new PriorityQueue<>();
        for (EtaContext.InProgress p : ctx.inProgress()) {
            double elapsed = Math.max(0, Duration.between(p.startedAt(), ctx.now()).toSeconds());
            // Never assume less than 20% of a service remains: customers often overrun the average.
            double remaining = Math.max(ctx.meanServiceSeconds() * 0.2, ctx.meanServiceSeconds() - elapsed);
            free.add(remaining);
        }
        int counters = Math.max(1, Math.max(ctx.openCounters(), ctx.inProgress().size()));
        while (free.size() < counters) {
            free.add(0.0);
        }
        return free;
    }

    private EtaResult build(EtaContext ctx, int ahead, int counters, double waitSeconds, Instant appointmentStart) {
        if (!ctx.businessOpen()) {
            return new EtaResult(ahead, null, null, null, null, null, null, "LOW", false, METHOD,
                    "The business is currently closed. Estimates are available once it opens.");
        }
        double rounds = ahead / (double) counters + 1.0;
        double spread = ctx.stdDevServiceSeconds() * Math.sqrt(rounds);
        double minSpread = Math.max(60.0, waitSeconds * 0.10);
        spread = Math.max(spread, minSpread);
        double low = Math.max(0, waitSeconds - spread);
        double high = waitSeconds + spread;

        Instant now = ctx.now();
        Instant turn = now.plusSeconds((long) waitSeconds);
        Instant turnMin = now.plusSeconds((long) low);
        Instant turnMax = now.plusSeconds((long) high);

        String confidence = confidence(ctx);
        StringBuilder note = new StringBuilder();
        if (ctx.openCounters() == 0) {
            note.append("No counter is currently open; estimate assumes one counter. ");
            confidence = "LOW";
        }
        if (ctx.closingTime() != null && turn.isAfter(ctx.closingTime())) {
            note.append("You may not be served before closing time today. ");
        }
        return new EtaResult(ahead, (int) Math.ceil(waitSeconds / 60.0), (int) Math.floor(low / 60.0),
                (int) Math.ceil(high / 60.0), turn, turnMin, turnMax, confidence, true, METHOD,
                note.length() == 0 ? null : note.toString().trim());
    }

    private String confidence(EtaContext ctx) {
        double cv = ctx.meanServiceSeconds() <= 0 ? 1 : ctx.stdDevServiceSeconds() / ctx.meanServiceSeconds();
        if (ctx.sampleCount() >= 30 && cv < 0.5) {
            return "HIGH";
        }
        if (ctx.sampleCount() >= 10) {
            return "MEDIUM";
        }
        return "LOW";
    }
}
