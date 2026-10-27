package com.smartqueue.service;

import com.smartqueue.entity.Business;
import com.smartqueue.repository.BreakPeriodRepository;
import com.smartqueue.repository.BusinessHoursRepository;
import com.smartqueue.repository.HolidayRepository;
import com.smartqueue.util.BusinessSchedule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class BusinessScheduleService {
    private final BusinessHoursRepository hoursRepo;
    private final BreakPeriodRepository breakRepo;
    private final HolidayRepository holidayRepo;

    public BusinessScheduleService(BusinessHoursRepository hoursRepo, BreakPeriodRepository breakRepo, HolidayRepository holidayRepo) {
        this.hoursRepo = hoursRepo;
        this.breakRepo = breakRepo;
        this.holidayRepo = holidayRepo;
    }

    @Transactional(readOnly = true)
    public BusinessSchedule load(Business business) {
        Long id = business.getId();
        return new BusinessSchedule(business.zone(), hoursRepo.findByBusinessId(id),
                breakRepo.findByBusinessId(id), holidayRepo.findByBusinessIdOrderByDateAsc(id));
    }

    @Transactional(readOnly = true)
    public boolean isOpen(Business business, Instant at) {
        return load(business).isOpen(at);
    }
}
