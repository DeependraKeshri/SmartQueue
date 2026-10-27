package com.smartqueue.intelligence;

import com.smartqueue.dto.InsightDtos.*;
import com.smartqueue.intelligence.DemandInput.DayHistory;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Transparent statistical model (no ML claims):
 *  expected arrivals(hour) = recency-weighted mean of the same hour on previous same weekdays (weight 0.85^k).
 *  Falls back to the last 28 days of any weekday if fewer than 3 same-weekday samples exist,
 *  and refuses to forecast (sufficientData=false) with fewer than 3 days of history.
 *  Queue length uses a single-server approximation on pooled capacity: rho^2/(1-rho) for rho&lt;1, else backlog + 5.
 */
@Component
public class StatisticalDemandPredictor implements DemandPredictor {
    private static final int MIN_DAYS = 3;
    private static final double DECAY = 0.85;
    private static final double TARGET_UTILIZATION = 0.8;

    @Override
    public DemandForecastDto predict(DemandInput in) {
        double avgMin = in.avgServiceSeconds() / 60.0;
        List<DayHistory> before = in.history().stream().filter(h -> h.date().isBefore(in.target()))
                .sorted(Comparator.comparing(DayHistory::date).reversed()).toList();
        List<DayHistory> sameWeekday = before.stream().filter(h -> h.date().getDayOfWeek() == in.target().getDayOfWeek()).limit(8).toList();
        List<DayHistory> basis;
        String method;
        String message = null;
        if (sameWeekday.size() >= MIN_DAYS) {
            basis = sameWeekday;
            method = "WEEKDAY_WEIGHTED_AVERAGE";
        } else if (before.size() >= MIN_DAYS) {
            LocalDate cutoff = in.target().minusDays(28);
            basis = before.stream().filter(h -> h.date().isAfter(cutoff)).toList();
            method = "RECENT_DAYS_AVERAGE";
            message = "Limited history for this weekday; using the average of recent days. Accuracy is reduced.";
            if (basis.size() < MIN_DAYS) {
                basis = before.stream().limit(MIN_DAYS).toList();
            }
        } else {
            return new DemandForecastDto(in.target(), false, before.size(), "NONE",
                    "Not enough history yet (need at least " + MIN_DAYS + " days of queue activity).",
                    in.activeCounters(), round1(avgMin), List.of(), List.of());
        }

        int counters = Math.max(1, in.activeCounters());
        double capacityPerHour = counters * 3600.0 / Math.max(60.0, in.avgServiceSeconds());
        List<HourForecast> hours = new ArrayList<>();
        for (int hour : in.openHours()) {
            double wSum = 0, vSum = 0;
            for (int i = 0; i < basis.size(); i++) {
                double w = Math.pow(DECAY, i);
                wSum += w;
                vSum += w * basis.get(i).joinedByHour()[hour];
            }
            double expected = wSum == 0 ? 0 : vSum / wSum;
            double util = expected / capacityPerHour;
            double queue = util < 1.0 ? (util * util) / (1.0 - util) : (expected - capacityPerHour) + 5.0;
            queue = Math.min(queue, 200.0);
            int recommended = expected <= 0 ? 0 : (int) Math.ceil(expected * in.avgServiceSeconds() / 3600.0 / TARGET_UTILIZATION);
            hours.add(new HourForecast(hour, round1(expected), round1(capacityPerHour), round1(util * 100.0),
                    round1(queue), recommended));
        }
        return new DemandForecastDto(in.target(), true, basis.size(), method, message, in.activeCounters(),
                round1(avgMin), hours, recommendations(in, hours));
    }

    private List<Recommendation> recommendations(DemandInput in, List<HourForecast> hours) {
        List<Recommendation> out = new ArrayList<>();
        int i = 0;
        while (i < hours.size()) {
            HourForecast h = hours.get(i);
            boolean high = h.utilizationPercent() >= 85.0 || h.recommendedCounters() > Math.max(1, in.activeCounters());
            if (!high) {
                i++;
                continue;
            }
            int j = i;
            int maxRec = h.recommendedCounters();
            double maxArr = h.expectedArrivals();
            while (j + 1 < hours.size() && hours.get(j + 1).hour() == hours.get(j).hour() + 1
                    && (hours.get(j + 1).utilizationPercent() >= 85.0
                    || hours.get(j + 1).recommendedCounters() > Math.max(1, in.activeCounters()))) {
                j++;
                maxRec = Math.max(maxRec, hours.get(j).recommendedCounters());
                maxArr = Math.max(maxArr, hours.get(j).expectedArrivals());
            }
            int from = hours.get(i).hour();
            int to = hours.get(j).hour() + 1;
            int missing = Math.max(0, maxRec - Math.max(1, in.activeCounters()));
            String msg = String.format("High demand expected %02d:00-%02d:00 (up to ~%.0f arrivals/hour).", from, to, maxArr);
            if (missing > 0 && in.totalCounters() > in.activeCounters()) {
                msg += " Consider activating " + Math.min(missing, in.totalCounters() - in.activeCounters()) + " additional counter(s).";
            } else if (missing > 0) {
                msg += " Consider adding " + missing + " counter(s).";
            }
            out.add(new Recommendation("COUNTER", missing > 1 ? "HIGH" : "MEDIUM", msg, from, to));
            if (maxRec > in.staffCount()) {
                out.add(new Recommendation("STAFF", "MEDIUM", String.format(
                        "Staffing may be insufficient %02d:00-%02d:00: %d staff available, ~%d recommended.",
                        from, to, in.staffCount(), maxRec), from, to));
            }
            i = j + 1;
        }
        boolean allLow = !hours.isEmpty() && hours.stream().allMatch(h -> h.utilizationPercent() < 20.0);
        if (allLow && in.activeCounters() > 1) {
            out.add(new Recommendation("INFO", "LOW", "Demand is expected to be low all day; fewer active counters may be sufficient.",
                    hours.get(0).hour(), hours.get(hours.size() - 1).hour() + 1));
        }
        return out;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
