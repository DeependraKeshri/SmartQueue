package com.smartqueue.service;

import com.smartqueue.audit.AuditService;
import com.smartqueue.common.ApiException;
import com.smartqueue.dto.BusinessDtos.*;
import com.smartqueue.dto.Mappers;
import com.smartqueue.entity.*;
import com.smartqueue.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/** Business profile, scheduling policy, operating hours, services and counters. All methods are tenant-scoped. */
@Service
public class BusinessConfigService {
    private final BusinessRepository businesses;
    private final QueuePolicyRepository policies;
    private final BusinessHoursRepository hoursRepo;
    private final BreakPeriodRepository breakRepo;
    private final HolidayRepository holidayRepo;
    private final BusinessServiceRepository services;
    private final ServiceQueueRepository queues;
    private final QueueEntryRepository entries;
    private final CounterRepository counters;
    private final AppUserRepository users;
    private final QueueService queueService;
    private final BusinessScheduleService scheduleService;
    private final AuditService audit;

    public BusinessConfigService(BusinessRepository businesses, QueuePolicyRepository policies,
                                 BusinessHoursRepository hoursRepo, BreakPeriodRepository breakRepo,
                                 HolidayRepository holidayRepo, BusinessServiceRepository services,
                                 ServiceQueueRepository queues, QueueEntryRepository entries, CounterRepository counters,
                                 AppUserRepository users, QueueService queueService,
                                 BusinessScheduleService scheduleService, AuditService audit) {
        this.businesses = businesses;
        this.policies = policies;
        this.hoursRepo = hoursRepo;
        this.breakRepo = breakRepo;
        this.holidayRepo = holidayRepo;
        this.services = services;
        this.queues = queues;
        this.entries = entries;
        this.counters = counters;
        this.users = users;
        this.queueService = queueService;
        this.scheduleService = scheduleService;
        this.audit = audit;
    }

    private Business business(Long id) {
        return businesses.findById(id).orElseThrow(() -> ApiException.notFound("Business not found"));
    }

    // ------------------------------------------------------------------ profile

    @Transactional(readOnly = true)
    public BusinessDto getBusiness(Long businessId) {
        return Mappers.business(business(businessId));
    }

    @Transactional
    public BusinessDto updateBusiness(Long businessId, UpdateBusinessRequest r) {
        Business b = business(businessId);
        Map<String, Object> changes = new LinkedHashMap<>();
        if (r.name() != null) { b.setName(r.name().trim()); changes.put("name", b.getName()); }
        if (r.description() != null) { b.setDescription(r.description()); }
        if (r.logoUrl() != null) { b.setLogoUrl(r.logoUrl()); }
        if (r.timezone() != null) {
            try {
                ZoneId.of(r.timezone());
            } catch (Exception e) {
                throw ApiException.badRequest("Unknown timezone: " + r.timezone());
            }
            b.setTimezone(r.timezone());
            changes.put("timezone", r.timezone());
        }
        if (r.tokenPrefix() != null) { b.setTokenPrefix(r.tokenPrefix().toUpperCase()); changes.put("tokenPrefix", b.getTokenPrefix()); }
        if (r.displayMode() != null) { b.setDisplayMode(r.displayMode()); changes.put("displayMode", r.displayMode().name()); }
        if (r.allowGuestJoin() != null) { b.setAllowGuestJoin(r.allowGuestJoin()); changes.put("allowGuestJoin", r.allowGuestJoin()); }
        if (r.showWaitOnDisplay() != null) { b.setShowWaitOnDisplay(r.showWaitOnDisplay()); }
        if (r.inAppNotificationsEnabled() != null) { b.setInAppNotificationsEnabled(r.inAppNotificationsEnabled()); }
        if (r.emailNotificationsEnabled() != null) { b.setEmailNotificationsEnabled(r.emailNotificationsEnabled()); }
        if (r.pushNotificationsEnabled() != null) { b.setPushNotificationsEnabled(r.pushNotificationsEnabled()); }
        if (r.autoConfirmAppointments() != null) { b.setAutoConfirmAppointments(r.autoConfirmAppointments()); }
        if (r.contactEmail() != null) { b.setContactEmail(r.contactEmail()); }
        if (r.contactPhone() != null) { b.setContactPhone(r.contactPhone()); }
        audit.record(businessId, "BUSINESS_UPDATED", "BUSINESS", businessId, changes);
        return Mappers.business(b);
    }

    // ------------------------------------------------------------------ queue policy

    @Transactional(readOnly = true)
    public PolicyDto getPolicy(Long businessId) {
        return Mappers.policy(policies.findByBusinessId(businessId).orElseGet(() -> QueuePolicy.defaults(business(businessId))));
    }

    @Transactional
    public PolicyDto updatePolicy(Long businessId, PolicyDto d) {
        QueuePolicy p = policies.findByBusinessId(businessId).orElseGet(() -> QueuePolicy.defaults(business(businessId)));
        p.setEmergencyWeight(d.emergencyWeight());
        p.setAppointmentWeight(d.appointmentWeight());
        p.setPriorityWeight(d.priorityWeight());
        p.setNormalWeight(d.normalWeight());
        p.setAgingPointsPerMinute(d.agingPointsPerMinute());
        p.setMaxWaitMinutes(d.maxWaitMinutes());
        p.setFairnessEveryN(d.fairnessEveryN());
        p.setEmergencyBypassesFairness(d.emergencyBypassesFairness());
        p.setAppointmentPolicy(d.appointmentPolicy());
        p.setAppointmentEarlyWindowMinutes(d.appointmentEarlyWindowMinutes());
        p.setAppointmentLateGraceMinutes(d.appointmentLateGraceMinutes());
        p.setNoShowGraceMinutes(d.noShowGraceMinutes());
        p.setAutoNoShow(d.autoNoShow());
        p.setMaxRecalls(d.maxRecalls());
        p.setApproachingThreshold(d.approachingThreshold());
        policies.save(p);
        audit.record(businessId, "QUEUE_POLICY_UPDATED", "QUEUE_POLICY", p.getId(), Map.of("appointmentPolicy", d.appointmentPolicy().name()));
        return Mappers.policy(p);
    }

    // ------------------------------------------------------------------ schedule

    @Transactional(readOnly = true)
    public ScheduleDto getSchedule(Long businessId) {
        Business b = business(businessId);
        List<HoursDto> hours = hoursRepo.findByBusinessId(businessId).stream()
                .sorted(Comparator.comparing(BusinessHours::getDayOfWeek)).map(Mappers::hours).toList();
        List<BreakDto> breaks = breakRepo.findByBusinessId(businessId).stream().map(Mappers::breakPeriod).toList();
        List<HolidayDto> holidays = holidayRepo.findByBusinessIdOrderByDateAsc(businessId).stream().map(Mappers::holiday).toList();
        return new ScheduleDto(hours, breaks, holidays, b.getTimezone(), scheduleService.isOpen(b, Instant.now()));
    }

    @Transactional
    public ScheduleDto updateHours(Long businessId, UpdateHoursRequest req) {
        Business b = business(businessId);
        Set<DayOfWeek> seen = new HashSet<>();
        Map<DayOfWeek, BusinessHours> existing = hoursRepo.findByBusinessId(businessId).stream()
                .collect(Collectors.toMap(BusinessHours::getDayOfWeek, h -> h));
        for (HoursDto d : req.days()) {
            if (!seen.add(d.dayOfWeek())) {
                throw ApiException.badRequest("Duplicate day: " + d.dayOfWeek());
            }
            if (!d.closed()) {
                if (d.openTime() == null || d.closeTime() == null || !d.openTime().isBefore(d.closeTime())) {
                    throw ApiException.badRequest("Opening time must be before closing time for " + d.dayOfWeek());
                }
            }
            BusinessHours h = existing.get(d.dayOfWeek());
            if (h == null) {
                h = new BusinessHours();
                h.setBusiness(b);
                h.setDayOfWeek(d.dayOfWeek());
            }
            h.setClosed(d.closed());
            h.setOpenTime(d.closed() ? null : d.openTime());
            h.setCloseTime(d.closed() ? null : d.closeTime());
            hoursRepo.save(h);
        }
        audit.record(businessId, "BUSINESS_HOURS_UPDATED", "BUSINESS", businessId);
        return getSchedule(businessId);
    }

    @Transactional
    public BreakDto addBreak(Long businessId, BreakDto d) {
        if (!d.startTime().isBefore(d.endTime())) {
            throw ApiException.badRequest("Break start must be before its end");
        }
        BreakPeriod bp = new BreakPeriod();
        bp.setBusiness(business(businessId));
        bp.setDayOfWeek(d.dayOfWeek());
        bp.setStartTime(d.startTime());
        bp.setEndTime(d.endTime());
        bp.setLabel(d.label());
        breakRepo.save(bp);
        audit.record(businessId, "BREAK_ADDED", "BREAK", bp.getId());
        return Mappers.breakPeriod(bp);
    }

    @Transactional
    public void deleteBreak(Long businessId, Long id) {
        BreakPeriod bp = breakRepo.findByIdAndBusinessId(id, businessId).orElseThrow(() -> ApiException.notFound("Break not found"));
        breakRepo.delete(bp);
        audit.record(businessId, "BREAK_REMOVED", "BREAK", id);
    }

    @Transactional
    public HolidayDto addHoliday(Long businessId, HolidayDto d) {
        if (holidayRepo.existsByBusinessIdAndDate(businessId, d.date())) {
            throw ApiException.conflict("A holiday already exists on that date");
        }
        Holiday h = new Holiday();
        h.setBusiness(business(businessId));
        h.setDate(d.date());
        h.setName(d.name());
        holidayRepo.save(h);
        audit.record(businessId, "HOLIDAY_ADDED", "HOLIDAY", h.getId(), Map.of("date", d.date().toString()));
        return Mappers.holiday(h);
    }

    @Transactional
    public void deleteHoliday(Long businessId, Long id) {
        Holiday h = holidayRepo.findByIdAndBusinessId(id, businessId).orElseThrow(() -> ApiException.notFound("Holiday not found"));
        holidayRepo.delete(h);
        audit.record(businessId, "HOLIDAY_REMOVED", "HOLIDAY", id);
    }

    // ------------------------------------------------------------------ services

    @Transactional(readOnly = true)
    public List<ServiceDto> listServices(Long businessId) {
        return services.findByBusinessIdAndDeletedFalseOrderByNameAsc(businessId).stream()
                .map(s -> Mappers.service(s, queues.findByServiceId(s.getId()).orElse(null))).toList();
    }

    @Transactional
    public ServiceDto createService(Long businessId, ServiceRequest r) {
        if (services.existsByBusinessIdAndNameIgnoreCaseAndDeletedFalse(businessId, r.name().trim())) {
            throw ApiException.conflict("A service with this name already exists");
        }
        Business b = business(businessId);
        BusinessService s = new BusinessService();
        s.setBusiness(b);
        apply(s, r);
        services.save(s);
        ServiceQueue q = s.isQueueEnabled() ? queueService.createQueueForService(b, s, null) : null;
        audit.record(businessId, "SERVICE_CREATED", "SERVICE", s.getId(), Map.of("name", s.getName()));
        return Mappers.service(s, q);
    }

    @Transactional
    public ServiceDto updateService(Long businessId, Long id, ServiceRequest r) {
        BusinessService s = services.findByIdAndBusinessIdAndDeletedFalse(id, businessId).orElseThrow(() -> ApiException.notFound("Service not found"));
        if (!s.getName().equalsIgnoreCase(r.name().trim())
                && services.existsByBusinessIdAndNameIgnoreCaseAndDeletedFalse(businessId, r.name().trim())) {
            throw ApiException.conflict("A service with this name already exists");
        }
        apply(s, r);
        ServiceQueue q = queues.findByServiceId(s.getId()).orElse(null);
        if (q == null && s.isQueueEnabled()) {
            q = queueService.createQueueForService(s.getBusiness(), s, null);
        }
        audit.record(businessId, "SERVICE_UPDATED", "SERVICE", s.getId(), Map.of("name", s.getName(), "active", s.isActive()));
        return Mappers.service(s, q);
    }

    private void apply(BusinessService s, ServiceRequest r) {
        s.setName(r.name().trim());
        if (r.description() != null) s.setDescription(r.description());
        if (r.estimatedDurationMinutes() != null) s.setEstimatedDurationMinutes(r.estimatedDurationMinutes());
        if (r.active() != null) s.setActive(r.active());
        if (r.queueEnabled() != null) s.setQueueEnabled(r.queueEnabled());
        if (r.appointmentsEnabled() != null) s.setAppointmentsEnabled(r.appointmentsEnabled());
        if (r.priorityAllowed() != null) s.setPriorityAllowed(r.priorityAllowed());
        if (r.slotIntervalMinutes() != null) s.setSlotIntervalMinutes(r.slotIntervalMinutes());
        if (r.maxAdvanceBookingDays() != null) s.setMaxAdvanceBookingDays(r.maxAdvanceBookingDays());
        if (r.minLeadMinutes() != null) s.setMinLeadMinutes(r.minLeadMinutes());
        if (r.tokenPrefix() != null) s.setTokenPrefix(r.tokenPrefix().toUpperCase());
    }

    /** Soft delete: history (entries, appointments, statistics) is preserved. */
    @Transactional
    public void deleteService(Long businessId, Long id) {
        BusinessService s = services.findByIdAndBusinessIdAndDeletedFalse(id, businessId).orElseThrow(() -> ApiException.notFound("Service not found"));
        ServiceQueue q = queues.findByServiceId(s.getId()).orElse(null);
        if (q != null) {
            long live = entries.countByQueueIdAndStatus(q.getId(), EntryStatus.WAITING)
                    + entries.countByQueueIdAndStatus(q.getId(), EntryStatus.CALLED)
                    + entries.countByQueueIdAndStatus(q.getId(), EntryStatus.SERVING)
                    + entries.countByQueueIdAndStatus(q.getId(), EntryStatus.SKIPPED);
            if (live > 0) {
                throw ApiException.conflict("Finish or cancel the " + live + " customer(s) still in this service's queue first");
            }
            q.setStatus(QueueStatus.INACTIVE);
        }
        s.setDeleted(true);
        s.setActive(false);
        audit.record(businessId, "SERVICE_DELETED", "SERVICE", id, Map.of("name", s.getName()));
    }

    // ------------------------------------------------------------------ counters

    @Transactional(readOnly = true)
    public List<CounterDto> listCounters(Long businessId) {
        return counters.findByBusinessIdOrderByNameAsc(businessId).stream().map(Mappers::counter).toList();
    }

    @Transactional
    public CounterDto createCounter(Long businessId, CounterRequest r) {
        if (counters.existsByBusinessIdAndNameIgnoreCase(businessId, r.name().trim())) {
            throw ApiException.conflict("A counter with this name already exists");
        }
        Counter c = new Counter();
        c.setBusiness(business(businessId));
        applyCounter(c, businessId, r);
        counters.save(c);
        audit.record(businessId, "COUNTER_CREATED", "COUNTER", c.getId(), Map.of("name", c.getName()));
        return Mappers.counter(c);
    }

    @Transactional
    public CounterDto updateCounter(Long businessId, Long id, CounterRequest r) {
        Counter c = counters.findByIdAndBusinessId(id, businessId).orElseThrow(() -> ApiException.notFound("Counter not found"));
        if (!c.getName().equalsIgnoreCase(r.name().trim()) && counters.existsByBusinessIdAndNameIgnoreCase(businessId, r.name().trim())) {
            throw ApiException.conflict("A counter with this name already exists");
        }
        applyCounter(c, businessId, r);
        if (!c.isActive()) {
            requireNoActiveCustomer(c);
            c.setStatus(CounterStatus.CLOSED);
        }
        audit.record(businessId, "COUNTER_UPDATED", "COUNTER", c.getId(), Map.of("name", c.getName()));
        return Mappers.counter(c);
    }

    private void applyCounter(Counter c, Long businessId, CounterRequest r) {
        c.setName(r.name().trim());
        if (r.active() != null) {
            c.setActive(r.active());
        }
        if (r.assignedStaffId() != null) {
            AppUser staff = users.findByIdAndBusinessId(r.assignedStaffId(), businessId)
                    .filter(u -> u.isActive() && (u.getRole() == Role.STAFF || u.getRole() == Role.MANAGER))
                    .orElseThrow(() -> ApiException.badRequest("Assigned staff member not found in this business"));
            for (Counter other : counters.findByAssignedStaffId(staff.getId())) {
                if (!other.getId().equals(c.getId())) {
                    throw ApiException.conflict(staff.getFullName() + " is already assigned to counter '" + other.getName() + "'");
                }
            }
            c.setAssignedStaff(staff);
        } else {
            c.setAssignedStaff(null);
        }
        Set<BusinessService> svc = new HashSet<>();
        if (r.serviceIds() != null) {
            for (Long sid : r.serviceIds()) {
                svc.add(services.findByIdAndBusinessIdAndDeletedFalse(sid, businessId)
                        .orElseThrow(() -> ApiException.badRequest("Service " + sid + " not found in this business")));
            }
        }
        c.getServices().clear();
        c.getServices().addAll(svc);
    }

    private void requireNoActiveCustomer(Counter c) {
        if (entries.existsByCounterIdAndStatusIn(c.getId(), EntryStatus.AT_COUNTER)) {
            throw ApiException.conflict("Finish or skip the customer currently at this counter first");
        }
    }

    @Transactional
    public CounterDto setCounterStatus(Long businessId, Long id, CounterStatus status, UserPrincipalView actor) {
        Counter c = counters.lockById(id).orElseThrow(() -> ApiException.notFound("Counter not found"));
        if (!c.getBusiness().getId().equals(businessId)) {
            throw ApiException.notFound("Counter not found");
        }
        if (actor.role() == Role.STAFF && (c.getAssignedStaff() == null || !c.getAssignedStaff().getId().equals(actor.userId()))) {
            throw ApiException.forbidden("You can only change the status of your own counter");
        }
        if (!c.isActive() && status == CounterStatus.OPEN) {
            throw ApiException.conflict("This counter is deactivated");
        }
        if (status != CounterStatus.OPEN) {
            requireNoActiveCustomer(c);
        }
        CounterStatus old = c.getStatus();
        c.setStatus(status);
        audit.record(businessId, "COUNTER_STATUS_CHANGED", "COUNTER", c.getId(), Map.of("from", old.name(), "to", status.name()));
        return Mappers.counter(c);
    }

    /** Minimal actor view to avoid leaking web-layer types into the service signature. */
    public record UserPrincipalView(Long userId, Role role) {}
}
