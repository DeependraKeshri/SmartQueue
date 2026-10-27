package com.smartqueue.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class AnalyticsDtos {
    private AnalyticsDtos() {}

    public record ServiceStat(Long serviceId, String serviceName, long served, double avgServiceMinutes, double avgWaitMinutes) {}
    public record CounterStat(Long counterId, String counterName, long served, double busyMinutes, double utilizationPercent) {}
    public record StaffStat(Long staffId, String staffName, long served, double avgServiceMinutes, double busyMinutes) {}
    public record HourStat(int hour, double avgCustomers, long totalCustomers) {}
    public record DayStat(LocalDate date, int totalCustomers, int completed, int cancelled, int noShows, double avgWaitMinutes) {}
    public record WeekdayStat(String dayOfWeek, long totalCustomers) {}
    public record QueueLengthPoint(Instant time, double avgLength, int maxLength) {}

    public record AnalyticsSummary(LocalDate from, LocalDate to, String timezone,
                                   long totalCustomers, long completed, long cancelled, long noShows,
                                   double completionRatePercent, double cancellationRatePercent, double noShowRatePercent,
                                   double avgWaitMinutes, double medianWaitMinutes,
                                   double maxWaitMinutes, double avgServiceMinutes,
                                   long appointmentsBooked, long appointmentsCompleted, long appointmentsNoShow,
                                   long appointmentsCancelled, double appointmentCompletionRatePercent,
                                   List<DayStat> daily, List<HourStat> customersPerHour, List<HourStat> peakHours,
                                   List<WeekdayStat> busiestDays, List<ServiceStat> services,
                                   List<CounterStat> counters, List<StaffStat> staff,
                                   List<QueueLengthPoint> queueLength, Map<String, String> notes) {}
}
