package com.smartqueue.job;

import com.smartqueue.analytics.AnalyticsRollupService;
import com.smartqueue.entity.EntryStatus;
import com.smartqueue.entity.QueueEntry;
import com.smartqueue.repository.QueueEntryRepository;
import com.smartqueue.service.AppointmentService;
import com.smartqueue.service.QueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Background jobs. Each unit of work runs in its own transaction and takes the same queue lock as user actions,
 * so jobs and users cannot race. NOTE: with several backend instances each job runs on every instance; the
 * operations are idempotent (state is re-checked under lock), so this is safe, merely redundant.
 */
@Component
@ConditionalOnProperty(name = "smartqueue.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class MaintenanceJobs {
    private static final Logger log = LoggerFactory.getLogger(MaintenanceJobs.class);

    private final QueueEntryRepository entries;
    private final QueueService queueService;
    private final AppointmentService appointmentService;
    private final AnalyticsRollupService analytics;
    private final com.smartqueue.repository.BusinessRepository businesses;

    public MaintenanceJobs(QueueEntryRepository entries, QueueService queueService,
                           AppointmentService appointmentService, AnalyticsRollupService analytics,
                           com.smartqueue.repository.BusinessRepository businesses) {
        this.businesses = businesses;
        this.entries = entries;
        this.queueService = queueService;
        this.appointmentService = appointmentService;
        this.analytics = analytics;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    public void calledCustomers() {
        try {
            for (QueueEntry e : entries.findByStatusAndCalledAtBefore(EntryStatus.CALLED, Instant.now().minusSeconds(30))) {
                try {
                    queueService.warnNoShow(e.getId());
                    queueService.autoNoShow(e.getId());
                } catch (Exception ex) {
                    log.warn("No-show processing failed for entry {}: {}", e.getId(), ex.getClass().getSimpleName());
                }
            }
        } catch (Exception ex) {
            log.warn("calledCustomers job failed: {}", ex.getClass().getSimpleName());
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void overdueAppointments() {
        try {
            appointmentService.autoMarkOverdueNoShows();
        } catch (Exception ex) {
            log.warn("overdueAppointments job failed: {}", ex.getClass().getSimpleName());
        }
    }

    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    public void sampleQueues() {
        try {
            analytics.sampleQueueLengths();
        } catch (Exception ex) {
            log.warn("sampleQueues job failed: {}", ex.getClass().getSimpleName());
        }
    }

    @Scheduled(cron = "0 5 * * * *")
    public void rollupYesterday() {
        for (com.smartqueue.entity.Business b : businesses.findByActiveTrueOrderByNameAsc()) {
            try {
                analytics.rollup(b.getId(), java.time.LocalDate.now(b.zone()).minusDays(1));
            } catch (Exception ex) {
                log.warn("Rollup failed for business {}: {}", b.getId(), ex.getClass().getSimpleName());
            }
        }
    }
}
