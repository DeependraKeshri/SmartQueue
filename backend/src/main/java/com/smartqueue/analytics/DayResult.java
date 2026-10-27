package com.smartqueue.analytics;

/** Pure result of aggregating one business day. */
public record DayResult(int totalCustomers, int completed, int cancelled, int noShows,
                        long sumWaitSeconds, long maxWaitSeconds, long medianWaitSeconds, long sumServiceSeconds,
                        int appointmentsBooked, int appointmentsCompleted, int appointmentsNoShow, int appointmentsCancelled,
                        int[] joinedByHour, int[] completedByHour) {
}
