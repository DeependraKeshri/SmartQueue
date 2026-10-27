package com.smartqueue.service;

import com.smartqueue.audit.AuditService;
import com.smartqueue.common.ApiException;
import com.smartqueue.dto.QueueDtos.*;
import com.smartqueue.entity.*;
import com.smartqueue.realtime.QueueChangedEvent;
import com.smartqueue.repository.*;
import com.smartqueue.scheduling.QueueScheduler;
import com.smartqueue.security.UserPrincipal;
import com.smartqueue.util.BusinessSchedule;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Write side of the live queue.
 *
 * Concurrency model: every state change first takes a PESSIMISTIC_WRITE lock on the ServiceQueue row
 * (and the Counter row when a counter is involved; counter first, then queues in ascending id order).
 * Entities are (re)loaded AFTER the lock is held, so decisions never use stale state.
 * Terminal operations are idempotent: repeating complete/cancel/skip/no-show returns the current state.
 */
@Service
public class QueueService {

    /** Command used by every code path that creates a queue entry. */
    public record JoinCommand(Long queueId, Long businessGuard, AppUser customer, String guestName, String guestPhone,
                              PriorityLevel priority, EntrySource source, Appointment appointment,
                              String idempotencyKey, String note) {}

    public record Created(QueueEntry entry, boolean duplicate) {}

    private final ServiceQueueRepository queues;
    private final QueueEntryRepository entries;
    private final CounterRepository counters;
    private final AppUserRepository users;
    private final BusinessServiceRepository services;
    private final BusinessRepository businesses;
    private final ServiceDurationRecordRepository durations;
    private final BusinessScheduleService scheduleService;
    private final QueueViewService view;
    private final QueueScheduler scheduler;
    private final AuditService audit;
    private final NotificationService notifications;
    private final ApplicationEventPublisher events;

    public QueueService(ServiceQueueRepository queues, QueueEntryRepository entries, CounterRepository counters,
                        AppUserRepository users, BusinessServiceRepository services, BusinessRepository businesses,
                        ServiceDurationRecordRepository durations, BusinessScheduleService scheduleService,
                        QueueViewService view, QueueScheduler scheduler, AuditService audit,
                        NotificationService notifications, ApplicationEventPublisher events) {
        this.queues = queues;
        this.entries = entries;
        this.counters = counters;
        this.users = users;
        this.services = services;
        this.businesses = businesses;
        this.durations = durations;
        this.scheduleService = scheduleService;
        this.view = view;
        this.scheduler = scheduler;
        this.audit = audit;
        this.notifications = notifications;
        this.events = events;
    }

    // =====================================================================================
    // Queue administration
    // =====================================================================================

    @Transactional
    public ServiceQueue createQueueForService(Business business, BusinessService service, String name) {
        if (queues.findByServiceId(service.getId()).isPresent()) {
            throw ApiException.conflict("This service already has a queue");
        }
        ServiceQueue q = new ServiceQueue();
        q.setBusiness(business);
        q.setService(service);
        q.setName(name == null || name.isBlank() ? service.getName() : name.trim());
        q.setTokenPrefix(allocatePrefix(business, service));
        q.setStatus(QueueStatus.ACTIVE);
        queues.save(q);
        audit.record(business.getId(), "QUEUE_CREATED", "QUEUE", q.getId(), Map.of("service", service.getName()));
        return q;
    }

    private String allocatePrefix(Business b, BusinessService s) {
        String explicit = s.getTokenPrefix() == null || s.getTokenPrefix().isBlank() ? null : s.getTokenPrefix().toUpperCase();
        String preferred = explicit != null ? explicit : b.getTokenPrefix().toUpperCase();
        if (!queues.existsByBusinessIdAndTokenPrefix(b.getId(), preferred)) {
            return preferred;
        }
        if (explicit != null) {
            throw ApiException.conflict("Token prefix '" + explicit + "' is already used by another queue");
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            String p = String.valueOf(c);
            if (!queues.existsByBusinessIdAndTokenPrefix(b.getId(), p)) {
                return p;
            }
        }
        for (int i = 1; i < 100; i++) {
            String p = preferred.substring(0, Math.min(preferred.length(), 3)) + i;
            if (!queues.existsByBusinessIdAndTokenPrefix(b.getId(), p)) {
                return p;
            }
        }
        throw ApiException.conflict("No free token prefix available");
    }

    @Transactional
    public QueueDto createQueue(Long businessId, CreateQueueRequest req) {
        Business b = businesses.findById(businessId).orElseThrow(() -> ApiException.notFound("Business not found"));
        BusinessService s = services.findByIdAndBusinessIdAndDeletedFalse(req.serviceId(), businessId)
                .orElseThrow(() -> ApiException.notFound("Service not found"));
        ServiceQueue q = createQueueForService(b, s, req.name());
        return view.queueDto(q);
    }

    @Transactional
    public QueueDto setQueueStatus(Long businessId, Long queueId, QueueStatusRequest req) {
        ServiceQueue q = lockQueueOfBusiness(queueId, businessId);
        QueueStatus old = q.getStatus();
        QueueStatus target = req.status();
        if (old == target) {
            return view.queueDto(q);
        }
        List<QueueEntry> waiting = entries.findByQueueIdAndStatusOrderByQueueTimeAscIdAsc(q.getId(), EntryStatus.WAITING);
        if (target == QueueStatus.INACTIVE && !waiting.isEmpty()) {
            if (!Boolean.TRUE.equals(req.cancelWaiting())) {
                throw ApiException.conflict("The queue has " + waiting.size()
                        + " waiting customer(s). Pause it instead, or set cancelWaiting=true to cancel them.");
            }
            Instant now = Instant.now();
            for (QueueEntry e : waiting) {
                e.setStatus(EntryStatus.CANCELLED);
                e.setCancelledAt(now);
                notifyEntry(e, NotificationType.BUSINESS_CANCELLATION, "Queue closed",
                        "The queue for " + q.getService().getName() + " was closed by the business. Your token "
                                + e.getTokenLabel() + " was cancelled.");
            }
        }
        q.setStatus(target);
        if (target == QueueStatus.ACTIVE && !waiting.isEmpty()) {
            for (QueueEntry e : waiting) {
                notifyEntry(e, NotificationType.SERVICE_AVAILABLE, "Queue resumed",
                        "The queue for " + q.getService().getName() + " is active again.");
            }
        }
        audit.record(businessId, "QUEUE_STATUS_CHANGED", "QUEUE", q.getId(),
                Map.of("from", old.name(), "to", target.name(), "cancelledWaiting", Boolean.TRUE.equals(req.cancelWaiting())));
        publish(q, null, "QUEUE_STATUS");
        return view.queueDto(q);
    }

    @Transactional
    public int announceDelay(Long businessId, Long queueId, int minutes, String message) {
        ServiceQueue q = lockQueueOfBusiness(queueId, businessId);
        List<QueueEntry> waiting = entries.findByQueueIdAndStatusOrderByQueueTimeAscIdAsc(q.getId(), EntryStatus.WAITING);
        String text = (message == null || message.isBlank())
                ? "We are running about " + minutes + " minutes behind. Thank you for your patience."
                : message;
        waiting.forEach(e -> notifyEntry(e, NotificationType.QUEUE_DELAY, "Queue delay", text));
        audit.record(businessId, "QUEUE_DELAY_ANNOUNCED", "QUEUE", q.getId(), Map.of("minutes", minutes, "notified", waiting.size()));
        return waiting.size();
    }

    // =====================================================================================
    // Joining
    // =====================================================================================

    /** Single entry-creation path used by customer join, guest join, staff walk-in and appointment check-in. */
    @Transactional
    public Created createEntry(JoinCommand c) {
        ServiceQueue q = queues.lockById(c.queueId()).orElseThrow(() -> ApiException.notFound("Queue not found"));
        if (c.businessGuard() != null && !q.getBusiness().getId().equals(c.businessGuard())) {
            throw ApiException.notFound("Queue not found");
        }
        if (c.idempotencyKey() != null) {
            Optional<QueueEntry> existing = entries.findByQueueIdAndIdempotencyKey(q.getId(), c.idempotencyKey());
            if (existing.isPresent()) {
                return new Created(existing.get(), true);
            }
        }
        Business b = q.getBusiness();
        BusinessService svc = q.getService();
        if (!b.isActive()) {
            throw ApiException.conflict("This business is not currently active");
        }
        if (q.getStatus() != QueueStatus.ACTIVE) {
            throw ApiException.conflict("This queue is not accepting new customers right now");
        }
        if (!svc.isActive() || !svc.isQueueEnabled() || svc.isDeleted()) {
            throw ApiException.conflict("This service is not available");
        }
        Instant now = Instant.now();
        BusinessSchedule schedule = scheduleService.load(b);
        if (!schedule.isOpen(now)) {
            throw ApiException.conflict("The business is currently closed");
        }
        if (c.customer() != null && entries.existsByQueueIdAndCustomerIdAndStatusIn(q.getId(), c.customer().getId(), EntryStatus.OCCUPYING)) {
            throw ApiException.conflict("You are already in this queue");
        }
        if (c.customer() == null && c.guestPhone() != null && !c.guestPhone().isBlank()
                && entries.existsByQueueIdAndGuestPhoneAndStatusIn(q.getId(), c.guestPhone(), EntryStatus.OCCUPYING)) {
            throw ApiException.conflict("This phone number is already in this queue");
        }

        LocalDate today = LocalDate.ofInstant(now, b.zone());
        if (!today.equals(q.getSequenceDate())) {
            q.setSequenceDate(today);
            q.setLastTokenNumber(0);
            q.setConsecutiveHighPriorityServed(0);
        }
        int number = q.getLastTokenNumber() + 1;
        q.setLastTokenNumber(number);

        QueueEntry e = new QueueEntry();
        e.setQueue(q);
        e.setBusiness(b);
        e.setService(svc);
        e.setCustomer(c.customer());
        e.setGuestName(c.customer() == null ? c.guestName() : null);
        e.setGuestPhone(c.customer() == null ? c.guestPhone() : null);
        e.setTokenNumber(number);
        e.setTokenLabel(q.getTokenPrefix() + String.format("%03d", number));
        e.setSequenceDate(today);
        e.setStatus(EntryStatus.WAITING);
        e.setSource(c.source());
        e.setPriority(c.priority() == null ? PriorityLevel.NORMAL : c.priority());
        e.setAppointment(c.appointment());
        e.setJoinedAt(now);
        e.setQueueTime(now);
        e.setIdempotencyKey(c.idempotencyKey());
        e.setNote(c.note());
        entries.save(e);

        audit.record(b.getId(), "QUEUE_JOINED", "QUEUE_ENTRY", e.getId(),
                Map.of("token", e.getTokenLabel(), "source", e.getSource().name(), "priority", e.getPriority().name()));
        notifyEntry(e, NotificationType.QUEUE_JOINED, "You joined the queue",
                "Your token is " + e.getTokenLabel() + " for " + svc.getName() + ".");
        publish(q, e.getId(), "JOINED");
        return new Created(e, false);
    }

    @Transactional
    public JoinResultDto joinAsCustomer(UUID queuePublicId, Long customerId, String idempotencyKey) {
        ServiceQueue q = queues.findByPublicId(queuePublicId).orElseThrow(() -> ApiException.notFound("Queue not found"));
        AppUser customer = users.findById(customerId).orElseThrow(() -> ApiException.unauthorized("Unknown user"));
        Created c = createEntry(new JoinCommand(q.getId(), null, customer, null, null, PriorityLevel.NORMAL,
                EntrySource.VIRTUAL, null, normalizeKey(idempotencyKey), null));
        return new JoinResultDto(view.customerView(c.entry()), c.duplicate());
    }

    @Transactional
    public JoinResultDto joinAsGuest(UUID queuePublicId, GuestJoinRequest req, String idempotencyKey) {
        ServiceQueue q = queues.findByPublicId(queuePublicId).orElseThrow(() -> ApiException.notFound("Queue not found"));
        if (!q.getBusiness().isAllowGuestJoin()) {
            throw ApiException.forbidden("This business requires you to sign in before joining the queue");
        }
        Created c = createEntry(new JoinCommand(q.getId(), null, null, req.guestName().trim(), blankToNull(req.guestPhone()),
                PriorityLevel.NORMAL, EntrySource.QR, null, normalizeKey(idempotencyKey), null));
        return new JoinResultDto(view.customerView(c.entry()), c.duplicate());
    }

    @Transactional
    public EntryDto addWalkIn(Long businessId, Long queueId, WalkInRequest req) {
        PriorityLevel level = req.priority() == null ? PriorityLevel.NORMAL : req.priority();
        if (level == PriorityLevel.APPOINTMENT) {
            throw ApiException.badRequest("APPOINTMENT priority is only assigned through appointment check-in");
        }
        ServiceQueue q = queues.findByIdAndBusinessId(queueId, businessId).orElseThrow(() -> ApiException.notFound("Queue not found"));
        if ((level == PriorityLevel.PRIORITY || level == PriorityLevel.EMERGENCY) && !q.getService().isPriorityAllowed()) {
            throw ApiException.badRequest("Priority levels are not enabled for this service");
        }
        Created c = createEntry(new JoinCommand(queueId, businessId, null, req.guestName().trim(), blankToNull(req.guestPhone()),
                level, EntrySource.WALK_IN, null, null, req.note()));
        return view.staffEntry(c.entry());
    }

    // =====================================================================================
    // Calling
    // =====================================================================================

    private record Candidate(ServiceQueue queue, QueueEntry entry, double score) {}

    @Transactional
    public Optional<EntryDto> callNext(Long businessId, UserPrincipal actor, CallNextRequest req) {
        Counter counter = lockCounterOfBusiness(req.counterId(), businessId);
        assertCounterAccess(counter, actor);
        assertCounterFree(counter);

        Set<Long> counterServiceIds = new HashSet<>();
        counter.getServices().forEach(s -> counterServiceIds.add(s.getId()));
        List<Long> queueIds;
        if (req.queueId() != null) {
            ServiceQueue q = queues.findByIdAndBusinessId(req.queueId(), businessId)
                    .orElseThrow(() -> ApiException.notFound("Queue not found"));
            if (!counterServiceIds.isEmpty() && !counterServiceIds.contains(q.getService().getId())) {
                throw ApiException.badRequest("This counter does not serve that queue's service");
            }
            queueIds = List.of(q.getId());
        } else {
            queueIds = queues.findByBusinessIdOrderByNameAsc(businessId).stream()
                    .filter(q -> counterServiceIds.isEmpty() || counterServiceIds.contains(q.getService().getId()))
                    .map(ServiceQueue::getId).sorted().toList();
        }
        List<ServiceQueue> locked = new ArrayList<>();
        for (Long id : queueIds.stream().sorted().toList()) {      // ascending order => no deadlocks
            queues.lockById(id).ifPresent(locked::add);
        }
        Instant now = Instant.now();
        QueuePolicy policy = view.policyFor(counter.getBusiness());
        List<Candidate> candidates = new ArrayList<>();
        for (ServiceQueue q : locked) {
            if (q.getStatus() == QueueStatus.INACTIVE) {
                continue;
            }
            List<QueueEntry> waiting = entries.findByQueueIdAndStatusOrderByQueueTimeAscIdAsc(q.getId(), EntryStatus.WAITING);
            scheduler.pickNext(waiting, policy, q.getConsecutiveHighPriorityServed(), now)
                    .ifPresent(e -> candidates.add(new Candidate(q, e, scheduler.score(e, policy, now))));
        }
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        Candidate best = candidates.stream().max(Comparator.comparingDouble(Candidate::score)).orElseThrow();
        QueueEntry e = best.entry();
        PriorityLevel applied = scheduler.effectiveLevel(e, policy, now);
        best.queue().setConsecutiveHighPriorityServed(
                scheduler.nextConsecutive(applied, best.queue().getConsecutiveHighPriorityServed(), policy));
        markCalled(e, counter, actor, now);

        audit.record(businessId, "QUEUE_CALL_NEXT", "QUEUE_ENTRY", e.getId(),
                Map.of("token", e.getTokenLabel(), "counterId", counter.getId(), "appliedPriority", applied.name()));
        publish(best.queue(), e.getId(), "CALLED");
        return Optional.of(view.staffEntry(e));
    }

    /** Manager/admin override: call a specific entry out of order. Reason is mandatory and audited. */
    @Transactional
    public EntryDto callSpecific(Long businessId, Long entryId, UserPrincipal actor, CallEntryRequest req) {
        Counter counter = lockCounterOfBusiness(req.counterId(), businessId);
        QueueEntry e = lockEntry(entryId, businessId);
        assertCounterFree(counter);
        if (e.getStatus() != EntryStatus.WAITING && e.getStatus() != EntryStatus.SKIPPED) {
            throw ApiException.conflict("Only waiting or skipped customers can be called manually");
        }
        ServiceQueue q = e.getQueue();
        QueuePolicy policy = view.policyFor(q.getBusiness());
        Instant now = Instant.now();
        PriorityLevel applied = scheduler.effectiveLevel(e, policy, now);
        q.setConsecutiveHighPriorityServed(scheduler.nextConsecutive(applied, q.getConsecutiveHighPriorityServed(), policy));
        markCalled(e, counter, actor, now);
        audit.record(businessId, "QUEUE_MANUAL_OVERRIDE", "QUEUE_ENTRY", e.getId(),
                Map.of("token", e.getTokenLabel(), "counterId", counter.getId(), "reason", req.reason()));
        publish(q, e.getId(), "CALLED");
        return view.staffEntry(e);
    }

    private void markCalled(QueueEntry e, Counter counter, UserPrincipal actor, Instant now) {
        e.setStatus(EntryStatus.CALLED);
        e.setCounter(counter);
        e.setServedBy(users.getReferenceById(actor.getId()));
        e.setCalledAt(now);
        e.setNoShowWarnedAt(null);
        notifyEntry(e, NotificationType.YOUR_TURN, "It's your turn",
                "Token " + e.getTokenLabel() + ": please go to " + counter.getName() + ".");
    }

    // =====================================================================================
    // Staff transitions (all idempotent where the target state is already reached)
    // =====================================================================================

    @Transactional
    public EntryDto start(Long businessId, Long entryId, UserPrincipal actor) {
        QueueEntry e = lockEntry(entryId, businessId);
        assertEntryAccess(e, actor);
        if (e.getStatus() == EntryStatus.SERVING) {
            return view.staffEntry(e);
        }
        requireTransition(e, EntryStatus.SERVING, "start serving");
        e.setStatus(EntryStatus.SERVING);
        e.setServingStartedAt(Instant.now());
        audit.record(businessId, "QUEUE_SERVICE_STARTED", "QUEUE_ENTRY", e.getId(), Map.of("token", e.getTokenLabel()));
        publish(e.getQueue(), e.getId(), "SERVING");
        return view.staffEntry(e);
    }

    @Transactional
    public EntryDto complete(Long businessId, Long entryId, UserPrincipal actor) {
        QueueEntry e = lockEntry(entryId, businessId);
        assertEntryAccess(e, actor);
        if (e.getStatus() == EntryStatus.COMPLETED) {
            return view.staffEntry(e);
        }
        requireTransition(e, EntryStatus.COMPLETED, "complete");
        Instant now = Instant.now();
        if (e.getServingStartedAt() == null) {
            e.setServingStartedAt(e.getCalledAt() != null ? e.getCalledAt() : now);
        }
        e.setStatus(EntryStatus.COMPLETED);
        e.setCompletedAt(now);

        ServiceDurationRecord r = new ServiceDurationRecord();
        r.setBusinessId(e.getBusiness().getId());
        r.setServiceId(e.getService().getId());
        r.setQueueId(e.getQueue().getId());
        r.setEntryId(e.getId());
        r.setCounterId(e.getCounter() == null ? null : e.getCounter().getId());
        r.setStaffId(e.getServedBy() == null ? null : e.getServedBy().getId());
        r.setDurationSeconds(Math.max(1, Duration.between(e.getServingStartedAt(), now).getSeconds()));
        r.setWaitSeconds(Math.max(0, Duration.between(e.getJoinedAt(), e.getServingStartedAt()).getSeconds()));
        r.setPriority(e.getPriority());
        r.setSource(e.getSource());
        r.setCompletedAt(now);
        durations.save(r);

        if (e.getAppointment() != null) {
            e.getAppointment().setStatus(AppointmentStatus.COMPLETED);
        }
        audit.record(businessId, "QUEUE_SERVICE_COMPLETED", "QUEUE_ENTRY", e.getId(),
                Map.of("token", e.getTokenLabel(), "durationSeconds", r.getDurationSeconds()));
        publish(e.getQueue(), e.getId(), "COMPLETED");
        return view.staffEntry(e);
    }

    @Transactional
    public EntryDto skip(Long businessId, Long entryId, UserPrincipal actor) {
        QueueEntry e = lockEntry(entryId, businessId);
        assertEntryAccess(e, actor);
        if (e.getStatus() == EntryStatus.SKIPPED) {
            return view.staffEntry(e);
        }
        requireTransition(e, EntryStatus.SKIPPED, "skip");
        e.setStatus(EntryStatus.SKIPPED);
        e.setSkippedAt(Instant.now());
        e.setSkipCount(e.getSkipCount() + 1);
        e.setCounter(null);
        audit.record(businessId, "QUEUE_SKIPPED", "QUEUE_ENTRY", e.getId(), Map.of("token", e.getTokenLabel()));
        publish(e.getQueue(), e.getId(), "SKIPPED");
        return view.staffEntry(e);
    }

    @Transactional
    public EntryDto recall(Long businessId, Long entryId, UserPrincipal actor, Long counterId) {
        // Lock order is ALWAYS counter -> queue (same as callNext) to rule out deadlocks.
        Counter requestedCounter = counterId == null ? null : lockCounterOfBusiness(counterId, businessId);
        QueueEntry e = lockEntry(entryId, businessId);
        QueuePolicy policy = view.policyFor(e.getBusiness());
        Instant now = Instant.now();
        if (e.getStatus() == EntryStatus.CALLED) {
            assertEntryAccess(e, actor);
            if (e.getRecallCount() >= policy.getMaxRecalls()) {
                throw ApiException.conflict("Maximum number of recalls reached for this customer");
            }
            e.setRecallCount(e.getRecallCount() + 1);
            e.setCalledAt(now);
            e.setNoShowWarnedAt(null);
            String counterName = e.getCounter() == null ? "the counter" : e.getCounter().getName();
            notifyEntry(e, NotificationType.YOUR_TURN, "Reminder: it's your turn",
                    "Token " + e.getTokenLabel() + ": please go to " + counterName + ".");
        } else if (e.getStatus() == EntryStatus.SKIPPED) {
            if (counterId == null) {
                throw ApiException.badRequest("counterId is required to recall a skipped customer");
            }
            if (e.getRecallCount() >= policy.getMaxRecalls()) {
                throw ApiException.conflict("Maximum number of recalls reached for this customer");
            }
            Counter counter = requestedCounter;
            assertCounterAccess(counter, actor);
            assertCounterFree(counter);
            e.setRecallCount(e.getRecallCount() + 1);
            markCalled(e, counter, actor, now);
        } else {
            throw ApiException.conflict("Only called or skipped customers can be recalled");
        }
        audit.record(businessId, "QUEUE_RECALLED", "QUEUE_ENTRY", e.getId(), Map.of("token", e.getTokenLabel()));
        publish(e.getQueue(), e.getId(), "RECALLED");
        return view.staffEntry(e);
    }

    @Transactional
    public EntryDto markNoShow(Long businessId, Long entryId, UserPrincipal actor) {
        QueueEntry e = lockEntry(entryId, businessId);
        if (e.getStatus() == EntryStatus.NO_SHOW) {
            return view.staffEntry(e);
        }
        assertEntryAccess(e, actor);
        applyNoShow(e, "manual");
        return view.staffEntry(e);
    }

    private void applyNoShow(QueueEntry e, String how) {
        requireTransition(e, EntryStatus.NO_SHOW, "mark as no-show");
        e.setStatus(EntryStatus.NO_SHOW);
        e.setNoShowAt(Instant.now());
        e.setCounter(null);
        if (e.getAppointment() != null) {
            e.getAppointment().setStatus(AppointmentStatus.NO_SHOW);
        }
        audit.record(e.getBusiness().getId(), "QUEUE_NO_SHOW", "QUEUE_ENTRY", e.getId(),
                Map.of("token", e.getTokenLabel(), "mode", how));
        publish(e.getQueue(), e.getId(), "NO_SHOW");
    }

    @Transactional
    public EntryDto moveToEnd(Long businessId, Long entryId, UserPrincipal actor) {
        QueueEntry e = lockEntry(entryId, businessId);
        if (e.getStatus() != EntryStatus.WAITING) {
            assertEntryAccess(e, actor);
            requireTransition(e, EntryStatus.WAITING, "move to the end of the queue");
        }
        e.setStatus(EntryStatus.WAITING);
        e.setQueueTime(Instant.now());
        e.setCounter(null);
        e.setCalledAt(null);
        e.setApproachingNotifiedAt(null);
        audit.record(businessId, "QUEUE_MOVED_TO_END", "QUEUE_ENTRY", e.getId(), Map.of("token", e.getTokenLabel()));
        publish(e.getQueue(), e.getId(), "MOVED");
        return view.staffEntry(e);
    }

    @Transactional
    public EntryDto setPriority(Long businessId, Long entryId, PriorityRequest req) {
        QueueEntry e = lockEntry(entryId, businessId);
        PriorityLevel target = req.priority();
        if (target == PriorityLevel.APPOINTMENT) {
            throw ApiException.badRequest("APPOINTMENT priority is only assigned through appointment check-in");
        }
        if ((target == PriorityLevel.PRIORITY || target == PriorityLevel.EMERGENCY) && !e.getService().isPriorityAllowed()) {
            throw ApiException.badRequest("Priority levels are not enabled for this service");
        }
        if (e.getStatus() != EntryStatus.WAITING) {
            throw ApiException.conflict("Priority can only be changed while the customer is waiting");
        }
        PriorityLevel old = e.getPriority();
        e.setPriority(target);
        audit.record(businessId, "QUEUE_PRIORITY_OVERRIDE", "QUEUE_ENTRY", e.getId(),
                Map.of("token", e.getTokenLabel(), "from", old.name(), "to", target.name(), "reason", req.reason()));
        publish(e.getQueue(), e.getId(), "PRIORITY");
        return view.staffEntry(e);
    }

    @Transactional
    public EntryDto cancelByStaff(Long businessId, Long entryId) {
        QueueEntry e = lockEntry(entryId, businessId);
        cancel(e, true);
        return view.staffEntry(e);
    }

    // =====================================================================================
    // Customer / guest actions
    // =====================================================================================

    @Transactional
    public EntryView cancelByCustomer(Long customerId, Long entryId) {
        Long queueId = entries.findQueueIdForCustomer(entryId, customerId)
                .orElseThrow(() -> ApiException.notFound("Queue entry not found"));
        queues.lockById(queueId).orElseThrow(() -> ApiException.notFound("Queue not found"));
        QueueEntry e = entries.findByIdAndCustomerId(entryId, customerId).orElseThrow(() -> ApiException.notFound("Queue entry not found"));
        cancel(e, false);
        return view.customerView(e);
    }

    @Transactional
    public EntryView cancelByPublicId(UUID publicId) {
        Long queueId = entries.findQueueIdByPublicId(publicId).orElseThrow(() -> ApiException.notFound("Queue entry not found"));
        queues.lockById(queueId).orElseThrow(() -> ApiException.notFound("Queue not found"));
        QueueEntry e = entries.findByPublicId(publicId).orElseThrow(() -> ApiException.notFound("Queue entry not found"));
        cancel(e, false);
        return view.customerView(e);
    }

    /** Cancels any live entry created from an appointment (used when the appointment itself is cancelled). */
    @Transactional
    public void cancelEntriesOfAppointment(Long appointmentId) {
        for (Long queueId : entries.findQueueIdsByAppointment(appointmentId)) {
            queues.lockById(queueId);
            for (QueueEntry e : entries.findByAppointmentIdAndStatusIn(appointmentId, EntryStatus.OCCUPYING)) {
                e.setStatus(EntryStatus.CANCELLED);
                e.setCancelledAt(Instant.now());
                e.setCounter(null);
                audit.record(e.getBusiness().getId(), "QUEUE_CANCELLED", "QUEUE_ENTRY", e.getId(),
                        Map.of("token", e.getTokenLabel(), "reason", "appointment cancelled"));
                publish(e.getQueue(), e.getId(), "CANCELLED");
            }
        }
    }

    private void cancel(QueueEntry e, boolean byBusiness) {
        if (e.getStatus() == EntryStatus.CANCELLED) {
            return;   // idempotent
        }
        if (e.getStatus() == EntryStatus.SERVING && !byBusiness) {
            throw ApiException.conflict("Service has already started; it can no longer be cancelled");
        }
        requireTransition(e, EntryStatus.CANCELLED, "cancel");
        e.setStatus(EntryStatus.CANCELLED);
        e.setCancelledAt(Instant.now());
        e.setCounter(null);
        if (e.getAppointment() != null && AppointmentStatus.BLOCKING.contains(e.getAppointment().getStatus())) {
            e.getAppointment().setStatus(AppointmentStatus.CANCELLED);
            e.getAppointment().setCancelledAt(Instant.now());
        }
        if (byBusiness) {
            notifyEntry(e, NotificationType.BUSINESS_CANCELLATION, "Your queue entry was cancelled",
                    "Token " + e.getTokenLabel() + " was cancelled by the business.");
        }
        audit.record(e.getBusiness().getId(), "QUEUE_CANCELLED", "QUEUE_ENTRY", e.getId(),
                Map.of("token", e.getTokenLabel(), "byBusiness", byBusiness));
        publish(e.getQueue(), e.getId(), "CANCELLED");
    }

    // =====================================================================================
    // System jobs (no authenticated actor)
    // =====================================================================================

    @Transactional
    public boolean autoNoShow(Long entryId) {
        Long queueId = entries.findQueueIdById(entryId).orElse(null);
        if (queueId == null) {
            return false;
        }
        queues.lockById(queueId);
        QueueEntry e = entries.findById(entryId).orElse(null);
        if (e == null || e.getStatus() != EntryStatus.CALLED || e.getCalledAt() == null) {
            return false;
        }
        QueuePolicy p = view.policyFor(e.getBusiness());
        if (!p.isAutoNoShow() || Instant.now().isBefore(e.getCalledAt().plus(Duration.ofMinutes(p.getNoShowGraceMinutes())))) {
            return false;
        }
        applyNoShow(e, "automatic");
        return true;
    }

    @Transactional
    public boolean warnNoShow(Long entryId) {
        QueueEntry e = entries.findById(entryId).orElse(null);
        if (e == null || e.getStatus() != EntryStatus.CALLED || e.getNoShowWarnedAt() != null || e.getCalledAt() == null) {
            return false;
        }
        QueuePolicy p = view.policyFor(e.getBusiness());
        Instant warnAt = e.getCalledAt().plusSeconds(p.getNoShowGraceMinutes() * 30L);   // half of the grace period
        if (Instant.now().isBefore(warnAt)) {
            return false;
        }
        e.setNoShowWarnedAt(Instant.now());
        notifyEntry(e, NotificationType.NO_SHOW_WARNING, "Please come to the counter",
                "Token " + e.getTokenLabel() + " will be marked as a no-show soon if you do not arrive.");
        return true;
    }

    // =====================================================================================
    // helpers
    // =====================================================================================

    private ServiceQueue lockQueueOfBusiness(Long queueId, Long businessId) {
        if (queues.findByIdAndBusinessId(queueId, businessId).isEmpty()) {
            throw ApiException.notFound("Queue not found");
        }
        return queues.lockById(queueId).orElseThrow(() -> ApiException.notFound("Queue not found"));
    }

    private Counter lockCounterOfBusiness(Long counterId, Long businessId) {
        Counter c = counters.lockById(counterId).orElseThrow(() -> ApiException.notFound("Counter not found"));
        if (!c.getBusiness().getId().equals(businessId)) {
            throw ApiException.notFound("Counter not found");
        }
        return c;
    }

    /** Locks the entry's queue FIRST, then loads the entry so its state is current. */
    private QueueEntry lockEntry(Long entryId, Long businessId) {
        Long queueId = entries.findQueueId(entryId, businessId).orElseThrow(() -> ApiException.notFound("Queue entry not found"));
        queues.lockById(queueId).orElseThrow(() -> ApiException.notFound("Queue not found"));
        return entries.findByIdAndBusinessId(entryId, businessId).orElseThrow(() -> ApiException.notFound("Queue entry not found"));
    }

    private void assertCounterAccess(Counter counter, UserPrincipal actor) {
        if (!counter.isActive() || counter.getStatus() != CounterStatus.OPEN) {
            throw ApiException.conflict("The counter is not open");
        }
        if (actor.getRole() == Role.STAFF) {
            AppUser assigned = counter.getAssignedStaff();
            if (assigned == null || !assigned.getId().equals(actor.getId())) {
                throw ApiException.forbidden("You are not assigned to this counter");
            }
        }
    }

    private void assertEntryAccess(QueueEntry e, UserPrincipal actor) {
        if (actor.getRole() == Role.STAFF && e.getCounter() != null) {
            AppUser assigned = e.getCounter().getAssignedStaff();
            if (assigned == null || !assigned.getId().equals(actor.getId())) {
                throw ApiException.forbidden("This customer is being served at another counter");
            }
        }
    }

    private void assertCounterFree(Counter counter) {
        if (entries.existsByCounterIdAndStatusIn(counter.getId(), EntryStatus.AT_COUNTER)) {
            throw ApiException.conflict("This counter already has an active customer. Complete or skip them first.");
        }
    }

    private void requireTransition(QueueEntry e, EntryStatus target, String verb) {
        if (!e.getStatus().canTransitionTo(target)) {
            throw ApiException.conflict("Cannot " + verb + " an entry that is " + e.getStatus());
        }
    }

    private void notifyEntry(QueueEntry e, NotificationType type, String title, String message) {
        if (e.getCustomer() != null) {
            notifications.notifyUser(e.getCustomer().getId(), e.getBusiness().getId(), type, title, message,
                    Map.of("entryPublicId", e.getPublicId().toString(), "token", e.getTokenLabel()));
        }
    }

    private void publish(ServiceQueue q, Long entryId, String reason) {
        events.publishEvent(new QueueChangedEvent(q.getBusiness().getId(), q.getId(), entryId, reason));
    }

    private static String normalizeKey(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String k = key.trim();
        if (k.length() > 64) {
            throw ApiException.badRequest("Idempotency-Key must be at most 64 characters");
        }
        return k;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
