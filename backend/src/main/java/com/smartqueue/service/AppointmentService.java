package com.smartqueue.service;

import com.smartqueue.audit.AuditService;
import com.smartqueue.common.ApiException;
import com.smartqueue.dto.AppointmentDtos.*;
import com.smartqueue.entity.*;
import com.smartqueue.realtime.AppointmentChangedEvent;
import com.smartqueue.repository.*;
import com.smartqueue.util.BusinessSchedule;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

/**
 * Appointment engine.
 * Capacity of a slot = number of active counters configured for the service (minimum 1).
 * Every mutation locks the service row (SELECT ... FOR UPDATE), so two customers can never take the last place of a slot.
 */
@Service
public class AppointmentService {
    private final AppointmentRepository appointments;
    private final BusinessServiceRepository services;
    private final CounterRepository counters;
    private final AppUserRepository users;
    private final ServiceQueueRepository queues;
    private final QueueEntryRepository entries;
    private final BusinessScheduleService scheduleService;
    private final QueueService queueService;
    private final QueueViewService view;
    private final NotificationService notifications;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final BusinessRepository businesses;

    public AppointmentService(BusinessRepository businesses, AppointmentRepository appointments, BusinessServiceRepository services,
                              CounterRepository counters, AppUserRepository users, ServiceQueueRepository queues,
                              QueueEntryRepository entries, BusinessScheduleService scheduleService,
                              QueueService queueService, QueueViewService view, NotificationService notifications,
                              AuditService audit, ApplicationEventPublisher events) {
        this.appointments = appointments;
        this.services = services;
        this.counters = counters;
        this.users = users;
        this.queues = queues;
        this.entries = entries;
        this.scheduleService = scheduleService;
        this.queueService = queueService;
        this.view = view;
        this.notifications = notifications;
        this.audit = audit;
        this.events = events;
        this.businesses = businesses;
    }

    // ------------------------------------------------------------------ slots

    @Transactional(readOnly = true)
    public List<SlotDto> slots(Long serviceId, LocalDate date) {
        BusinessService svc = services.findByIdAndDeletedFalse(serviceId).orElseThrow(() -> ApiException.notFound("Service not found"));
        requireBookable(svc);
        return computeSlots(svc, date, null);
    }

    private void requireBookable(BusinessService svc) {
        if (!svc.isActive() || !svc.isAppointmentsEnabled() || !svc.getBusiness().isActive()) {
            throw ApiException.conflict("Appointments are not available for this service");
        }
    }

    List<SlotDto> computeSlots(BusinessService svc, LocalDate date, Long excludeAppointmentId) {
        Business b = svc.getBusiness();
        BusinessSchedule schedule = scheduleService.load(b);
        Instant now = Instant.now();
        LocalDate today = LocalDate.ofInstant(now, b.zone());
        if (date.isBefore(today) || date.isAfter(today.plusDays(svc.getMaxAdvanceBookingDays()))) {
            return List.of();
        }
        int duration = svc.getEstimatedDurationMinutes();
        int step = Math.max(5, svc.getSlotIntervalMinutes());
        int capacity = (int) Math.max(1, counters.countActiveForService(svc.getId()));
        Instant dayStart = date.atStartOfDay(b.zone()).toInstant();
        Instant dayEnd = date.plusDays(1).atStartOfDay(b.zone()).toInstant();
        List<Appointment> existing = appointments.findOverlapping(svc.getId(), AppointmentStatus.BLOCKING, dayStart, dayEnd)
                .stream().filter(a -> !a.getId().equals(excludeAppointmentId)).toList();
        Instant earliest = now.plus(Duration.ofMinutes(svc.getMinLeadMinutes()));

        List<SlotDto> out = new ArrayList<>();
        for (BusinessSchedule.Interval iv : schedule.openIntervals(date)) {
            int startMin = iv.start().toSecondOfDay() / 60;
            int endMin = iv.end().toSecondOfDay() / 60;
            for (int m = startMin; m + duration <= endMin; m += step) {
                Instant slotStart = schedule.toInstant(date, LocalTime.ofSecondOfDay(m * 60L));
                Instant slotEnd = slotStart.plus(Duration.ofMinutes(duration));
                if (slotStart.isBefore(earliest)) {
                    continue;
                }
                long taken = existing.stream().filter(a -> a.getStartAt().isBefore(slotEnd) && a.getEndAt().isAfter(slotStart)).count();
                out.add(new SlotDto(slotStart, slotEnd, capacity, (int) Math.max(0, capacity - taken)));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ booking

    @Transactional
    public AppointmentDto book(Long customerId, BookRequest req, String idempotencyKey) {
        BusinessService svc = services.lockById(req.serviceId()).orElseThrow(() -> ApiException.notFound("Service not found"));
        if (svc.isDeleted()) {
            throw ApiException.notFound("Service not found");
        }
        String key = normalizeKey(idempotencyKey);
        if (key != null) {
            Optional<Appointment> existing = appointments.findByCustomerIdAndIdempotencyKey(customerId, key);
            if (existing.isPresent()) {
                return toDto(existing.get());
            }
        }
        requireBookable(svc);
        AppUser customer = users.findById(customerId).orElseThrow(() -> ApiException.unauthorized("Unknown user"));
        Business b = svc.getBusiness();
        SlotDto slot = findSlot(svc, req.startAt(), null);
        if (!appointments.findCustomerOverlapping(customerId, AppointmentStatus.BLOCKING, slot.startAt(), slot.endAt()).isEmpty()) {
            throw ApiException.conflict("You already have an appointment at that time");
        }
        Appointment a = new Appointment();
        a.setBusiness(b);
        a.setService(svc);
        a.setCustomer(customer);
        a.setStartAt(slot.startAt());
        a.setEndAt(slot.endAt());
        a.setStatus(b.isAutoConfirmAppointments() ? AppointmentStatus.CONFIRMED : AppointmentStatus.BOOKED);
        a.setNotes(req.notes());
        a.setIdempotencyKey(key);
        appointments.save(a);

        audit.record(b.getId(), "APPOINTMENT_BOOKED", "APPOINTMENT", a.getId(),
                Map.of("service", svc.getName(), "startAt", a.getStartAt().toString()));
        notifyCustomer(a, NotificationType.APPOINTMENT_CONFIRMED,
                a.getStatus() == AppointmentStatus.CONFIRMED ? "Appointment confirmed" : "Appointment requested",
                svc.getName() + " at " + b.getName() + " on " + a.getStartAt() + ".");
        publish(a, "BOOKED");
        return toDto(a);
    }

    private SlotDto findSlot(BusinessService svc, Instant start, Long excludeId) {
        LocalDate date = LocalDate.ofInstant(start, svc.getBusiness().zone());
        SlotDto slot = computeSlots(svc, date, excludeId).stream().filter(s -> s.startAt().equals(start)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("The selected time is not an available slot"));
        if (slot.remaining() <= 0) {
            throw ApiException.conflict("This time slot is no longer available");
        }
        return slot;
    }

    @Transactional
    public AppointmentDto reschedule(Long customerId, Long id, RescheduleRequest req) {
        Appointment a = appointments.findByIdAndCustomerId(id, customerId).orElseThrow(() -> ApiException.notFound("Appointment not found"));
        BusinessService svc = services.lockById(a.getService().getId()).orElseThrow(() -> ApiException.notFound("Service not found"));
        // re-read after lock
        a = appointments.findByIdAndCustomerId(id, customerId).orElseThrow(() -> ApiException.notFound("Appointment not found"));
        if (!AppointmentStatus.PENDING.contains(a.getStatus())) {
            throw ApiException.conflict("Only upcoming appointments can be rescheduled");
        }
        if (!a.getStartAt().isAfter(Instant.now())) {
            throw ApiException.conflict("This appointment has already started");
        }
        requireBookable(svc);
        SlotDto slot = findSlot(svc, req.startAt(), a.getId());
        final Long selfId = a.getId();
        boolean overlap = appointments.findCustomerOverlapping(customerId, AppointmentStatus.BLOCKING, slot.startAt(), slot.endAt())
                .stream().anyMatch(o -> !o.getId().equals(selfId));
        if (overlap) {
            throw ApiException.conflict("You already have another appointment at that time");
        }
        Instant old = a.getStartAt();
        a.setStartAt(slot.startAt());
        a.setEndAt(slot.endAt());
        a.setRescheduleCount(a.getRescheduleCount() + 1);
        a.setStatus(a.getBusiness().isAutoConfirmAppointments() ? AppointmentStatus.CONFIRMED : AppointmentStatus.BOOKED);
        audit.record(a.getBusiness().getId(), "APPOINTMENT_RESCHEDULED", "APPOINTMENT", a.getId(),
                Map.of("from", old.toString(), "to", slot.startAt().toString()));
        notifyCustomer(a, NotificationType.APPOINTMENT_CHANGED, "Appointment rescheduled",
                "Your appointment is now on " + a.getStartAt() + ".");
        publish(a, "RESCHEDULED");
        return toDto(a);
    }

    @Transactional
    public AppointmentDto cancelByCustomer(Long customerId, Long id, String reason) {
        Appointment a = appointments.findByIdAndCustomerId(id, customerId).orElseThrow(() -> ApiException.notFound("Appointment not found"));
        return cancel(a.getId(), a.getService().getId(), reason, false);
    }

    @Transactional
    public AppointmentDto cancelByStaff(Long businessId, Long id, String reason) {
        Appointment a = appointments.findByIdAndBusinessId(id, businessId).orElseThrow(() -> ApiException.notFound("Appointment not found"));
        return cancel(a.getId(), a.getService().getId(), reason, true);
    }

    private AppointmentDto cancel(Long id, Long serviceId, String reason, boolean byBusiness) {
        services.lockById(serviceId);
        Appointment a = appointments.findById(id).orElseThrow(() -> ApiException.notFound("Appointment not found"));
        if (a.getStatus() == AppointmentStatus.CANCELLED) {
            return toDto(a);   // idempotent
        }
        if (!AppointmentStatus.BLOCKING.contains(a.getStatus())) {
            throw ApiException.conflict("This appointment can no longer be cancelled (" + a.getStatus() + ")");
        }
        boolean wasCheckedIn = a.getStatus() == AppointmentStatus.CHECKED_IN;
        a.setStatus(AppointmentStatus.CANCELLED);
        a.setCancelledAt(Instant.now());
        a.setCancelReason(reason);
        if (wasCheckedIn) {
            queueService.cancelEntriesOfAppointment(a.getId());
        }
        audit.record(a.getBusiness().getId(), "APPOINTMENT_CANCELLED", "APPOINTMENT", a.getId(),
                Map.of("byBusiness", byBusiness, "reason", reason == null ? "" : reason));
        notifyCustomer(a, byBusiness ? NotificationType.BUSINESS_CANCELLATION : NotificationType.APPOINTMENT_CANCELLED,
                "Appointment cancelled", "Your appointment on " + a.getStartAt() + " was cancelled.");
        publish(a, "CANCELLED");
        return toDto(a);
    }

    // ------------------------------------------------------------------ staff lifecycle

    @Transactional
    public AppointmentDto confirm(Long businessId, Long id) {
        Appointment a = appointments.findByIdAndBusinessId(id, businessId).orElseThrow(() -> ApiException.notFound("Appointment not found"));
        if (a.getStatus() == AppointmentStatus.CONFIRMED) {
            return toDto(a);
        }
        if (a.getStatus() != AppointmentStatus.BOOKED) {
            throw ApiException.conflict("Only booked appointments can be confirmed");
        }
        a.setStatus(AppointmentStatus.CONFIRMED);
        audit.record(businessId, "APPOINTMENT_CONFIRMED", "APPOINTMENT", a.getId());
        notifyCustomer(a, NotificationType.APPOINTMENT_CONFIRMED, "Appointment confirmed",
                "Your appointment on " + a.getStartAt() + " is confirmed.");
        publish(a, "CONFIRMED");
        return toDto(a);
    }

    @Transactional
    public AppointmentDto markNoShow(Long businessId, Long id) {
        Appointment a = appointments.findByIdAndBusinessId(id, businessId).orElseThrow(() -> ApiException.notFound("Appointment not found"));
        if (a.getStatus() == AppointmentStatus.NO_SHOW) {
            return toDto(a);
        }
        if (!AppointmentStatus.PENDING.contains(a.getStatus())) {
            throw ApiException.conflict("Only upcoming appointments can be marked as no-show");
        }
        if (Instant.now().isBefore(a.getStartAt())) {
            throw ApiException.conflict("The appointment has not started yet");
        }
        a.setStatus(AppointmentStatus.NO_SHOW);
        audit.record(businessId, "APPOINTMENT_NO_SHOW", "APPOINTMENT", a.getId(), Map.of("mode", "manual"));
        publish(a, "NO_SHOW");
        return toDto(a);
    }

    @Transactional
    public AppointmentDto complete(Long businessId, Long id) {
        Appointment a = appointments.findByIdAndBusinessId(id, businessId).orElseThrow(() -> ApiException.notFound("Appointment not found"));
        if (a.getStatus() == AppointmentStatus.COMPLETED) {
            return toDto(a);
        }
        if (a.getStatus() != AppointmentStatus.CHECKED_IN && a.getStatus() != AppointmentStatus.CONFIRMED) {
            throw ApiException.conflict("This appointment cannot be completed from status " + a.getStatus());
        }
        a.setStatus(AppointmentStatus.COMPLETED);
        audit.record(businessId, "APPOINTMENT_COMPLETED", "APPOINTMENT", a.getId());
        publish(a, "COMPLETED");
        return toDto(a);
    }

    // ------------------------------------------------------------------ check-in (appointment -> live queue)

    @Transactional
    public AppointmentDto checkInByCustomer(Long customerId, Long id) {
        Appointment a = appointments.findByIdAndCustomerId(id, customerId).orElseThrow(() -> ApiException.notFound("Appointment not found"));
        return checkIn(a);
    }

    @Transactional
    public AppointmentDto checkInByStaff(Long businessId, Long id) {
        Appointment a = appointments.findByIdAndBusinessId(id, businessId).orElseThrow(() -> ApiException.notFound("Appointment not found"));
        return checkIn(a);
    }

    private AppointmentDto checkIn(Appointment a) {
        if (a.getStatus() == AppointmentStatus.CHECKED_IN) {
            return toDto(a);
        }
        if (!AppointmentStatus.PENDING.contains(a.getStatus())) {
            throw ApiException.conflict("This appointment cannot be checked in (" + a.getStatus() + ")");
        }
        Instant now = Instant.now();
        QueuePolicy policy = view.policyFor(a.getBusiness());
        long earlyMinutes = Math.max(60, policy.getAppointmentEarlyWindowMinutes());
        if (now.isBefore(a.getStartAt().minus(Duration.ofMinutes(earlyMinutes)))) {
            throw ApiException.conflict("Check-in opens " + earlyMinutes + " minutes before the appointment");
        }
        if (now.isAfter(a.getEndAt())) {
            throw ApiException.conflict("The appointment time has passed");
        }
        ServiceQueue q = queues.findByServiceId(a.getService().getId())
                .orElseThrow(() -> ApiException.conflict("This service has no live queue"));
        QueueService.Created created = queueService.createEntry(new QueueService.JoinCommand(q.getId(), a.getBusiness().getId(),
                a.getCustomer(), null, null, PriorityLevel.APPOINTMENT, EntrySource.APPOINTMENT, a, null, null));
        a.setStatus(AppointmentStatus.CHECKED_IN);
        audit.record(a.getBusiness().getId(), "APPOINTMENT_CHECKED_IN", "APPOINTMENT", a.getId(),
                Map.of("token", created.entry().getTokenLabel()));
        publish(a, "CHECKED_IN");
        return toDto(a);
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public List<AppointmentDto> forCustomer(Long customerId) {
        return appointments.findByCustomerIdOrderByStartAtDesc(customerId).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<AppointmentDto> forBusinessOnDate(Long businessId, LocalDate date) {
        Business b = businesses.findById(businessId).orElseThrow(() -> ApiException.notFound("Business not found"));
        Instant from = date.atStartOfDay(b.zone()).toInstant();
        Instant to = date.plusDays(1).atStartOfDay(b.zone()).toInstant();
        return appointments.findByBusinessIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(businessId, from, to)
                .stream().map(this::toDto).toList();
    }

    // ------------------------------------------------------------------ system job

    @Transactional
    public int autoMarkOverdueNoShows() {
        Instant cutoff = Instant.now().minus(Duration.ofMinutes(15));
        List<Appointment> overdue = appointments.findByStatusInAndEndAtBefore(AppointmentStatus.PENDING, cutoff);
        for (Appointment a : overdue) {
            a.setStatus(AppointmentStatus.NO_SHOW);
            audit.record(a.getBusiness().getId(), "APPOINTMENT_NO_SHOW", "APPOINTMENT", a.getId(), Map.of("mode", "automatic"));
            publish(a, "NO_SHOW");
        }
        return overdue.size();
    }

    // ------------------------------------------------------------------ helpers

    AppointmentDto toDto(Appointment a) {
        UUID entryPublicId = entries.findFirstByAppointmentIdOrderByIdDesc(a.getId()).map(QueueEntry::getPublicId).orElse(null);
        Business b = a.getBusiness();
        return new AppointmentDto(a.getId(), a.getService().getId(), a.getService().getName(), b.getId(), b.getName(),
                b.getPublicId(), a.getCustomer().getId(), a.getCustomer().getFullName(), a.getStartAt(), a.getEndAt(),
                a.getStatus(), a.getNotes(), a.getRescheduleCount(), entryPublicId, b.getTimezone());
    }

    private void notifyCustomer(Appointment a, NotificationType type, String title, String message) {
        notifications.notifyUser(a.getCustomer().getId(), a.getBusiness().getId(), type, title, message,
                Map.of("appointmentId", a.getId()));
    }

    private void publish(Appointment a, String reason) {
        events.publishEvent(new AppointmentChangedEvent(a.getBusiness().getId(), a.getId(), reason));
    }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (key.length() > 64) {
            throw ApiException.badRequest("Idempotency-Key must be at most 64 characters");
        }
        return key.trim();
    }
}
