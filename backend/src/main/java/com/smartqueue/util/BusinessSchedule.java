package com.smartqueue.util;

import com.smartqueue.entity.BreakPeriod;
import com.smartqueue.entity.BusinessHours;
import com.smartqueue.entity.Holiday;

import java.time.*;
import java.util.*;

/** Immutable snapshot of a business's operating schedule. Pure logic (no DB access) so it is trivially testable. */
public class BusinessSchedule {
    public record Interval(LocalTime start, LocalTime end) {
        public boolean contains(LocalTime t) {
            return !t.isBefore(start) && t.isBefore(end);
        }
    }

    private final ZoneId zone;
    private final Map<DayOfWeek, BusinessHours> hours = new EnumMap<>(DayOfWeek.class);
    private final List<BreakPeriod> breaks;
    private final Set<LocalDate> holidays = new HashSet<>();

    public BusinessSchedule(ZoneId zone, Collection<BusinessHours> hours, Collection<BreakPeriod> breaks, Collection<Holiday> holidays) {
        this.zone = zone;
        hours.forEach(h -> this.hours.put(h.getDayOfWeek(), h));
        this.breaks = new ArrayList<>(breaks);
        holidays.forEach(h -> this.holidays.add(h.getDate()));
    }

    public ZoneId zone() {
        return zone;
    }

    public boolean isHoliday(LocalDate date) {
        return holidays.contains(date);
    }

    /** Open segments of a date after removing breaks. Empty on holidays / closed days / no configuration. */
    public List<Interval> openIntervals(LocalDate date) {
        if (holidays.contains(date)) {
            return List.of();
        }
        BusinessHours h = hours.get(date.getDayOfWeek());
        if (h == null || h.isClosed() || h.getOpenTime() == null || h.getCloseTime() == null
                || !h.getOpenTime().isBefore(h.getCloseTime())) {
            return List.of();
        }
        List<Interval> segments = new ArrayList<>(List.of(new Interval(h.getOpenTime(), h.getCloseTime())));
        for (BreakPeriod b : breaks) {
            if (b.getDayOfWeek() != null && b.getDayOfWeek() != date.getDayOfWeek()) {
                continue;
            }
            List<Interval> next = new ArrayList<>();
            for (Interval s : segments) {
                if (!b.getStartTime().isBefore(s.end()) || !b.getEndTime().isAfter(s.start())) {
                    next.add(s);
                    continue;
                }
                if (b.getStartTime().isAfter(s.start())) {
                    next.add(new Interval(s.start(), b.getStartTime()));
                }
                if (b.getEndTime().isBefore(s.end())) {
                    next.add(new Interval(b.getEndTime(), s.end()));
                }
            }
            segments = next;
        }
        segments.sort(Comparator.comparing(Interval::start));
        return segments;
    }

    public boolean isOpen(Instant at) {
        ZonedDateTime z = at.atZone(zone);
        LocalTime t = z.toLocalTime();
        return openIntervals(z.toLocalDate()).stream().anyMatch(i -> i.contains(t));
    }

    /** End of the open segment containing {@code at}, if open. */
    public Optional<Instant> closingTime(Instant at) {
        ZonedDateTime z = at.atZone(zone);
        LocalTime t = z.toLocalTime();
        return openIntervals(z.toLocalDate()).stream().filter(i -> i.contains(t)).findFirst()
                .map(i -> z.toLocalDate().atTime(i.end()).atZone(zone).toInstant());
    }

    public long openSeconds(LocalDate date) {
        return openIntervals(date).stream()
                .mapToLong(i -> Duration.between(i.start(), i.end()).getSeconds()).sum();
    }

    public Instant toInstant(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(zone).toInstant();
    }
}
