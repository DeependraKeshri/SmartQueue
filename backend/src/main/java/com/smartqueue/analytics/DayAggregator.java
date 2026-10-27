package com.smartqueue.analytics;

import com.smartqueue.entity.Appointment;
import com.smartqueue.entity.AppointmentStatus;
import com.smartqueue.entity.EntryStatus;
import com.smartqueue.entity.QueueEntry;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Pure aggregation logic (unit tested). */
public final class DayAggregator {
    private DayAggregator() {}

    public static long median(List<Long> values) {
        if (values.isEmpty()) {
            return 0;
        }
        List<Long> s = new ArrayList<>(values);
        Collections.sort(s);
        int n = s.size();
        return n % 2 == 1 ? s.get(n / 2) : Math.round((s.get(n / 2 - 1) + s.get(n / 2)) / 2.0);
    }

    public static DayResult aggregate(LocalDate date, ZoneId zone, List<QueueEntry> entries, List<Appointment> appointments) {
        int completed = 0, cancelled = 0, noShows = 0;
        long sumWait = 0, maxWait = 0, sumService = 0;
        List<Long> waits = new ArrayList<>();
        int[] joined = new int[24];
        int[] done = new int[24];
        for (QueueEntry e : entries) {
            joined[e.getJoinedAt().atZone(zone).getHour()]++;
            switch (e.getStatus()) {
                case COMPLETED -> {
                    completed++;
                    if (e.getServingStartedAt() != null) {
                        long wait = Math.max(0, Duration.between(e.getJoinedAt(), e.getServingStartedAt()).getSeconds());
                        waits.add(wait);
                        sumWait += wait;
                        maxWait = Math.max(maxWait, wait);
                        if (e.getCompletedAt() != null) {
                            sumService += Math.max(0, Duration.between(e.getServingStartedAt(), e.getCompletedAt()).getSeconds());
                        }
                    }
                    if (e.getCompletedAt() != null && e.getCompletedAt().atZone(zone).toLocalDate().equals(date)) {
                        done[e.getCompletedAt().atZone(zone).getHour()]++;
                    }
                }
                case CANCELLED -> cancelled++;
                case NO_SHOW -> noShows++;
                default -> { }
            }
        }
        int aBooked = appointments.size(), aDone = 0, aNoShow = 0, aCancelled = 0;
        for (Appointment a : appointments) {
            if (a.getStatus() == AppointmentStatus.COMPLETED) aDone++;
            else if (a.getStatus() == AppointmentStatus.NO_SHOW) aNoShow++;
            else if (a.getStatus() == AppointmentStatus.CANCELLED) aCancelled++;
        }
        return new DayResult(entries.size(), completed, cancelled, noShows, sumWait, maxWait, median(waits), sumService,
                aBooked, aDone, aNoShow, aCancelled, joined, done);
    }
}
