package com.smartqueue.intelligence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.cache.StateCache;
import com.smartqueue.common.ApiException;
import com.smartqueue.dto.InsightDtos.DemandForecastDto;
import com.smartqueue.entity.*;
import com.smartqueue.repository.*;
import com.smartqueue.service.BusinessScheduleService;
import com.smartqueue.util.BusinessSchedule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/** Gathers history and delegates to the DemandPredictor. Forecasts change slowly, so they are cached for 10 minutes. */
@Service
public class InsightsService {
    private final BusinessRepository businesses;
    private final HourlyStatRepository hourly;
    private final ServiceDurationRecordRepository durations;
    private final BusinessServiceRepository services;
    private final CounterRepository counters;
    private final AppUserRepository users;
    private final BusinessScheduleService scheduleService;
    private final DemandPredictor predictor;
    private final StateCache cache;
    private final ObjectMapper mapper;

    public InsightsService(BusinessRepository businesses, HourlyStatRepository hourly,
                           ServiceDurationRecordRepository durations, BusinessServiceRepository services,
                           CounterRepository counters, AppUserRepository users, BusinessScheduleService scheduleService,
                           DemandPredictor predictor, StateCache cache, ObjectMapper mapper) {
        this.businesses = businesses;
        this.hourly = hourly;
        this.durations = durations;
        this.services = services;
        this.counters = counters;
        this.users = users;
        this.scheduleService = scheduleService;
        this.predictor = predictor;
        this.cache = cache;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public DemandForecastDto forecast(Long businessId, LocalDate date) {
        String key = "forecast:" + businessId + ":" + date;
        Optional<DemandForecastDto> cached = cache.get(key).flatMap(json -> {
            try {
                return Optional.of(mapper.readValue(json, DemandForecastDto.class));
            } catch (Exception e) {
                return Optional.empty();
            }
        });
        if (cached.isPresent()) {
            return cached.get();
        }
        Business b = businesses.findById(businessId).orElseThrow(() -> ApiException.notFound("Business not found"));
        BusinessSchedule schedule = scheduleService.load(b);

        Map<LocalDate, int[]> byDay = new TreeMap<>();
        for (HourlyStat h : hourly.findByBusinessIdAndStatDateBetween(businessId, date.minusDays(56), date.minusDays(1))) {
            byDay.computeIfAbsent(h.getStatDate(), d -> new int[24])[h.getHourOfDay()] += h.getJoined();
        }
        List<DemandInput.DayHistory> history = byDay.entrySet().stream()
                .map(e -> new DemandInput.DayHistory(e.getKey(), e.getValue())).toList();

        Instant now = Instant.now();
        double avgSeconds = 0;
        long n = 0;
        for (Object[] r : durations.byService(businessId, now.minus(Duration.ofDays(30)), now)) {
            long c = ((Number) r[1]).longValue();
            avgSeconds += ((Number) r[2]).doubleValue() * c;
            n += c;
        }
        if (n > 0) {
            avgSeconds /= n;
        } else {
            avgSeconds = services.findByBusinessIdAndDeletedFalseOrderByNameAsc(businessId).stream()
                    .mapToInt(BusinessService::getEstimatedDurationMinutes).average().orElse(10) * 60.0;
        }
        List<Counter> all = counters.findByBusinessIdOrderByNameAsc(businessId);
        int active = (int) all.stream().filter(Counter::isActive).count();
        int staff = users.findByBusinessIdAndRoleInAndActiveTrue(businessId, List.of(Role.STAFF, Role.MANAGER)).size();

        TreeSet<Integer> hours = new TreeSet<>();
        for (BusinessSchedule.Interval iv : schedule.openIntervals(date)) {
            int endHour = iv.end().getMinute() == 0 && iv.end().getSecond() == 0 ? iv.end().getHour() : iv.end().getHour() + 1;
            for (int h = iv.start().getHour(); h < endHour && h < 24; h++) {
                hours.add(h);
            }
        }
        DemandForecastDto result = predictor.predict(new DemandInput(date, history, avgSeconds, active, all.size(), staff,
                new ArrayList<>(hours)));
        try {
            cache.put(key, mapper.writeValueAsString(result), Duration.ofMinutes(10));
        } catch (Exception ignored) {
            // best effort
        }
        return result;
    }
}
