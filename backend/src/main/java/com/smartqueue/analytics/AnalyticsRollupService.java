package com.smartqueue.analytics;

import com.smartqueue.entity.*;
import com.smartqueue.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Computes and stores per-day / per-hour aggregates so reports never scan raw history for past days. */
@Service
public class AnalyticsRollupService {
    private final QueueEntryRepository entries;
    private final AppointmentRepository appointments;
    private final DailyStatRepository daily;
    private final HourlyStatRepository hourly;
    private final BusinessRepository businesses;
    private final ServiceQueueRepository queues;
    private final QueueLengthSampleRepository samples;

    public AnalyticsRollupService(QueueEntryRepository entries, AppointmentRepository appointments,
                                  DailyStatRepository daily, HourlyStatRepository hourly, BusinessRepository businesses,
                                  ServiceQueueRepository queues, QueueLengthSampleRepository samples) {
        this.entries = entries;
        this.appointments = appointments;
        this.daily = daily;
        this.hourly = hourly;
        this.businesses = businesses;
        this.queues = queues;
        this.samples = samples;
    }

    /** Live aggregation of one day without persisting (used for "today"). */
    @Transactional(readOnly = true)
    public DayResult computeLive(Long businessId, LocalDate date) {
        Business b = businesses.findById(businessId).orElseThrow();
        return compute(businessId, date, b.zone());
    }

    private DayResult compute(Long businessId, LocalDate date, ZoneId zone) {
        Instant from = date.atStartOfDay(zone).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(zone).toInstant();
        List<QueueEntry> e = entries.findByBusinessIdAndJoinedAtGreaterThanEqualAndJoinedAtLessThan(businessId, from, to);
        List<Appointment> a = appointments.findByBusinessIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(businessId, from, to);
        return DayAggregator.aggregate(date, zone, e, a);
    }

    /** Idempotent upsert of the stored aggregates for a completed day. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void rollup(Long businessId, LocalDate date) {
        Business b = businesses.findById(businessId).orElse(null);
        if (b == null) {
            return;
        }
        DayResult r = compute(businessId, date, b.zone());
        DailyStat d = daily.findByBusinessIdAndStatDate(businessId, date).orElseGet(() -> {
            DailyStat n = new DailyStat();
            n.setBusinessId(businessId);
            n.setStatDate(date);
            return n;
        });
        d.setTotalCustomers(r.totalCustomers());
        d.setCompleted(r.completed());
        d.setCancelled(r.cancelled());
        d.setNoShows(r.noShows());
        d.setSumWaitSeconds(r.sumWaitSeconds());
        d.setMaxWaitSeconds(r.maxWaitSeconds());
        d.setMedianWaitSeconds(r.medianWaitSeconds());
        d.setSumServiceSeconds(r.sumServiceSeconds());
        d.setAppointmentsBooked(r.appointmentsBooked());
        d.setAppointmentsCompleted(r.appointmentsCompleted());
        d.setAppointmentsNoShow(r.appointmentsNoShow());
        d.setAppointmentsCancelled(r.appointmentsCancelled());
        daily.save(d);

        hourly.deleteForDay(businessId, date);
        for (int h = 0; h < 24; h++) {
            if (r.joinedByHour()[h] > 0 || r.completedByHour()[h] > 0) {
                HourlyStat hs = new HourlyStat();
                hs.setBusinessId(businessId);
                hs.setStatDate(date);
                hs.setHourOfDay(h);
                hs.setJoined(r.joinedByHour()[h]);
                hs.setCompleted(r.completedByHour()[h]);
                hourly.save(hs);
            }
        }
    }

    /** Samples the number of waiting customers of every queue (feeds queue-length-over-time). */
    @Transactional
    public void sampleQueueLengths() {
        Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        for (ServiceQueue q : queues.findAll()) {
            if (q.getStatus() == QueueStatus.INACTIVE) {
                continue;
            }
            QueueLengthSample s = new QueueLengthSample();
            s.setBusinessId(q.getBusiness().getId());
            s.setQueueId(q.getId());
            s.setSampledAt(now);
            s.setQueueLength((int) entries.countByQueueIdAndStatus(q.getId(), EntryStatus.WAITING));
            samples.save(s);
        }
        samples.deleteOlderThan(now.minus(Duration.ofDays(90)));
    }
}
