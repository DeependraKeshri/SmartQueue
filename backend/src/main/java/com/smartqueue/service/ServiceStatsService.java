package com.smartqueue.service;

import com.smartqueue.repository.ServiceDurationRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Blends configured service duration with observed history (Bayesian-style shrinkage toward the configured value). */
@Service
public class ServiceStatsService {
    public record Stats(double meanSeconds, double stdDevSeconds, long samples) {}

    private static final int PRIOR_WEIGHT = 5;
    private final ServiceDurationRecordRepository repo;

    public ServiceStatsService(ServiceDurationRecordRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public Stats statsFor(Long serviceId, int configuredMinutes) {
        double prior = Math.max(1, configuredMinutes) * 60.0;
        List<Object[]> rows = repo.statsForService(serviceId, Instant.now().minus(Duration.ofDays(30)));
        long n = 0;
        double sum = 0;
        double sumSq = 0;
        if (!rows.isEmpty() && rows.get(0)[0] != null) {
            Object[] r = rows.get(0);
            n = ((Number) r[0]).longValue();
            sum = r[1] == null ? 0 : ((Number) r[1]).doubleValue();
            sumSq = r[2] == null ? 0 : ((Number) r[2]).doubleValue();
        }
        return compute(prior, n, sum, sumSq);
    }

    /** Pure function, unit tested. */
    public static Stats compute(double priorSeconds, long n, double sum, double sumSq) {
        if (n == 0) {
            return new Stats(priorSeconds, priorSeconds * 0.3, 0);
        }
        double observedMean = sum / n;
        double mean = (n * observedMean + PRIOR_WEIGHT * priorSeconds) / (n + PRIOR_WEIGHT);
        double sd;
        if (n >= 5) {
            double variance = Math.max(0, (sumSq - n * observedMean * observedMean) / (n - 1));
            sd = Math.sqrt(variance);
        } else {
            sd = mean * 0.3;
        }
        return new Stats(mean, sd, n);
    }
}
