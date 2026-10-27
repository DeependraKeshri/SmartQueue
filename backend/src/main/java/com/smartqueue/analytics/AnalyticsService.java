package com.smartqueue.analytics;

import com.smartqueue.common.ApiException;
import com.smartqueue.dto.AnalyticsDtos.*;
import com.smartqueue.entity.*;
import com.smartqueue.repository.*;
import com.smartqueue.service.BusinessScheduleService;
import com.smartqueue.util.BusinessSchedule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {
    private static final int MAX_DAYS = 366;

    private final BusinessRepository businesses;
    private final DailyStatRepository daily;
    private final HourlyStatRepository hourly;
    private final ServiceDurationRecordRepository durations;
    private final QueueLengthSampleRepository samples;
    private final BusinessServiceRepository services;
    private final CounterRepository counters;
    private final AppUserRepository users;
    private final AnalyticsRollupService rollup;
    private final BusinessScheduleService scheduleService;

    public AnalyticsService(BusinessRepository businesses, DailyStatRepository daily, HourlyStatRepository hourly,
                            ServiceDurationRecordRepository durations, QueueLengthSampleRepository samples,
                            BusinessServiceRepository services, CounterRepository counters, AppUserRepository users,
                            AnalyticsRollupService rollup, BusinessScheduleService scheduleService) {
        this.businesses = businesses;
        this.daily = daily;
        this.hourly = hourly;
        this.durations = durations;
        this.samples = samples;
        this.services = services;
        this.counters = counters;
        this.users = users;
        this.rollup = rollup;
        this.scheduleService = scheduleService;
    }

    public AnalyticsSummary day(Long businessId, LocalDate date) {
        return summary(businessId, date, date);
    }

    public AnalyticsSummary week(Long businessId, LocalDate anchor) {
        LocalDate from = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return summary(businessId, from, from.plusDays(6));
    }

    public AnalyticsSummary month(Long businessId, LocalDate anchor) {
        return summary(businessId, anchor.withDayOfMonth(1), anchor.with(TemporalAdjusters.lastDayOfMonth()));
    }

    /** Not @Transactional as a whole: rollups run in their own transactions; reads below are short and independent. */
    public AnalyticsSummary summary(Long businessId, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw ApiException.badRequest("'to' must not be before 'from'");
        }
        if (ChronoUnit.DAYS.between(from, to) + 1 > MAX_DAYS) {
            throw ApiException.badRequest("Range too large (max " + MAX_DAYS + " days)");
        }
        Business b = businesses.findById(businessId).orElseThrow(() -> ApiException.notFound("Business not found"));
        ZoneId zone = b.zone();
        LocalDate today = LocalDate.now(zone);
        LocalDate createdDate = LocalDate.ofInstant(b.getCreatedAt(), zone);

        // 1) make sure every completed day in range has stored aggregates
        Set<LocalDate> have = daily.findByBusinessIdAndStatDateBetweenOrderByStatDateAsc(businessId, from, to).stream()
                .map(DailyStat::getStatDate).collect(Collectors.toSet());
        for (LocalDate d = from.isBefore(createdDate) ? createdDate : from; !d.isAfter(to) && d.isBefore(today); d = d.plusDays(1)) {
            if (!have.contains(d)) {
                try {
                    rollup.rollup(businessId, d);
                } catch (Exception ignored) {
                    // concurrent rollup of the same day: the other transaction wins
                }
            }
        }
        List<DailyStat> stored = daily.findByBusinessIdAndStatDateBetweenOrderByStatDateAsc(businessId, from, to);

        // 2) combine stored days with live "today"
        List<DayStat> dayStats = new ArrayList<>();
        long total = 0, completed = 0, cancelled = 0, noShows = 0, sumWait = 0, sumService = 0, maxWait = 0;
        long aBooked = 0, aDone = 0, aNo = 0, aCancel = 0;
        double weightedMedian = 0;
        Map<DayOfWeek, Long> weekday = new EnumMap<>(DayOfWeek.class);
        long[] hourTotals = new long[24];
        int daysWithData = 0;

        for (DailyStat s : stored) {
            if (s.getStatDate().isBefore(today)) {
                daysWithData++;
                total += s.getTotalCustomers();
                completed += s.getCompleted();
                cancelled += s.getCancelled();
                noShows += s.getNoShows();
                sumWait += s.getSumWaitSeconds();
                sumService += s.getSumServiceSeconds();
                maxWait = Math.max(maxWait, s.getMaxWaitSeconds());
                weightedMedian += (double) s.getMedianWaitSeconds() * s.getCompleted();
                aBooked += s.getAppointmentsBooked();
                aDone += s.getAppointmentsCompleted();
                aNo += s.getAppointmentsNoShow();
                aCancel += s.getAppointmentsCancelled();
                weekday.merge(s.getStatDate().getDayOfWeek(), (long) s.getTotalCustomers(), Long::sum);
                dayStats.add(new DayStat(s.getStatDate(), s.getTotalCustomers(), s.getCompleted(), s.getCancelled(),
                        s.getNoShows(), s.getCompleted() == 0 ? 0 : round1(s.getSumWaitSeconds() / 60.0 / s.getCompleted())));
            }
        }
        for (HourlyStat h : hourly.findByBusinessIdAndStatDateBetween(businessId, from, to)) {
            if (h.getStatDate().isBefore(today)) {
                hourTotals[h.getHourOfDay()] += h.getJoined();
            }
        }
        if (!today.isBefore(from) && !today.isAfter(to) && !today.isBefore(createdDate)) {
            DayResult r = rollup.computeLive(businessId, today);
            daysWithData++;
            total += r.totalCustomers();
            completed += r.completed();
            cancelled += r.cancelled();
            noShows += r.noShows();
            sumWait += r.sumWaitSeconds();
            sumService += r.sumServiceSeconds();
            maxWait = Math.max(maxWait, r.maxWaitSeconds());
            weightedMedian += (double) r.medianWaitSeconds() * r.completed();
            aBooked += r.appointmentsBooked();
            aDone += r.appointmentsCompleted();
            aNo += r.appointmentsNoShow();
            aCancel += r.appointmentsCancelled();
            weekday.merge(today.getDayOfWeek(), (long) r.totalCustomers(), Long::sum);
            for (int h = 0; h < 24; h++) {
                hourTotals[h] += r.joinedByHour()[h];
            }
            dayStats.add(new DayStat(today, r.totalCustomers(), r.completed(), r.cancelled(), r.noShows(),
                    r.completed() == 0 ? 0 : round1(r.sumWaitSeconds() / 60.0 / r.completed())));
        }
        dayStats.sort(Comparator.comparing(DayStat::date));

        final int days = Math.max(1, daysWithData);
        List<HourStat> perHour = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            if (hourTotals[h] > 0) {
                perHour.add(new HourStat(h, round1(hourTotals[h] / (double) days), hourTotals[h]));
            }
        }
        List<HourStat> peak = perHour.stream().sorted(Comparator.comparingLong(HourStat::totalCustomers).reversed()).limit(3).toList();
        List<WeekdayStat> busiest = weekday.entrySet().stream()
                .sorted(Map.Entry.<DayOfWeek, Long>comparingByValue().reversed())
                .map(e -> new WeekdayStat(e.getKey().name(), e.getValue())).toList();

        // 3) detail breakdowns straight from service duration history (indexed range queries)
        Instant fromI = from.atStartOfDay(zone).toInstant();
        Instant toI = to.plusDays(1).atStartOfDay(zone).toInstant();
        BusinessSchedule schedule = scheduleService.load(b);
        List<ServiceStat> serviceStats = serviceStats(businessId, fromI, toI);
        List<CounterStat> counterStats = counterStats(businessId, fromI, toI, schedule, from, to, today);
        List<StaffStat> staffStats = staffStats(businessId, fromI, toI);
        List<QueueLengthPoint> lengths = queueLength(businessId, fromI, toI);

        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("medianWait", "Range median is the completed-customer-weighted average of daily medians (exact for single-day ranges).");
        notes.put("utilization", "Counter utilization = busy service time / scheduled open time of the business within the range (up to today).");
        notes.put("today", "Today's figures are computed live and may still change.");

        return new AnalyticsSummary(from, to, b.getTimezone(), total, completed, cancelled, noShows,
                pct(completed, total), pct(cancelled, total), pct(noShows, total),
                completed == 0 ? 0 : round1(sumWait / 60.0 / completed),
                completed == 0 ? 0 : round1(weightedMedian / completed / 60.0),
                round1(maxWait / 60.0), completed == 0 ? 0 : round1(sumService / 60.0 / completed),
                aBooked, aDone, aNo, aCancel, pct(aDone, aBooked - aCancel),
                dayStats, perHour, peak, busiest, serviceStats, counterStats, staffStats, lengths, notes);
    }

    private List<ServiceStat> serviceStats(Long businessId, Instant from, Instant to) {
        List<Object[]> rows = durations.byService(businessId, from, to);
        Map<Long, String> names = services.findAllById(rows.stream().map(r -> (Long) r[0]).toList()).stream()
                .collect(Collectors.toMap(BusinessService::getId, BusinessService::getName));
        return rows.stream().map(r -> new ServiceStat((Long) r[0], names.getOrDefault((Long) r[0], "Service " + r[0]),
                ((Number) r[1]).longValue(), round1(((Number) r[2]).doubleValue() / 60.0),
                round1(((Number) r[3]).doubleValue() / 60.0))).toList();
    }

    private List<CounterStat> counterStats(Long businessId, Instant from, Instant to, BusinessSchedule schedule,
                                           LocalDate fromD, LocalDate toD, LocalDate today) {
        long available = 0;
        for (LocalDate d = fromD; !d.isAfter(toD) && !d.isAfter(today); d = d.plusDays(1)) {
            available += schedule.openSeconds(d);
        }
        final long availableSeconds = available;
        List<Object[]> rows = durations.byCounter(businessId, from, to);
        Map<Long, String> names = counters.findAllById(rows.stream().map(r -> (Long) r[0]).toList()).stream()
                .collect(Collectors.toMap(Counter::getId, Counter::getName));
        return rows.stream().map(r -> {
            double busy = ((Number) r[2]).doubleValue();
            double util = availableSeconds == 0 ? 0 : Math.min(100.0, busy / availableSeconds * 100.0);
            return new CounterStat((Long) r[0], names.getOrDefault((Long) r[0], "Counter " + r[0]),
                    ((Number) r[1]).longValue(), round1(busy / 60.0), round1(util));
        }).toList();
    }

    private List<StaffStat> staffStats(Long businessId, Instant from, Instant to) {
        List<Object[]> rows = durations.byStaff(businessId, from, to);
        Map<Long, String> names = users.findAllById(rows.stream().map(r -> (Long) r[0]).toList()).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getFullName));
        return rows.stream().map(r -> new StaffStat((Long) r[0], names.getOrDefault((Long) r[0], "Staff " + r[0]),
                ((Number) r[1]).longValue(), round1(((Number) r[3]).doubleValue() / 60.0),
                round1(((Number) r[2]).doubleValue() / 60.0))).toList();
    }

    private List<QueueLengthPoint> queueLength(Long businessId, Instant from, Instant to) {
        List<QueueLengthSample> raw = samples
                .findByBusinessIdAndSampledAtGreaterThanEqualAndSampledAtLessThanOrderBySampledAtAsc(businessId, from, to);
        Map<Instant, Integer> totalPerSample = new TreeMap<>();
        raw.forEach(s -> totalPerSample.merge(s.getSampledAt(), s.getQueueLength(), Integer::sum));
        Map<Instant, List<Integer>> perHour = new TreeMap<>();
        totalPerSample.forEach((t, v) -> perHour.computeIfAbsent(t.truncatedTo(ChronoUnit.HOURS), k -> new ArrayList<>()).add(v));
        return perHour.entrySet().stream().map(e -> new QueueLengthPoint(e.getKey(),
                round1(e.getValue().stream().mapToInt(Integer::intValue).average().orElse(0)),
                e.getValue().stream().mapToInt(Integer::intValue).max().orElse(0))).toList();
    }

    private static double pct(double part, double whole) {
        return whole <= 0 ? 0 : round1(part * 100.0 / whole);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
