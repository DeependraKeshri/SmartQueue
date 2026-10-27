package com.smartqueue.service;

import com.smartqueue.dto.QueueDtos.*;
import com.smartqueue.entity.*;
import com.smartqueue.eta.EtaContext;
import com.smartqueue.eta.EtaEstimator;
import com.smartqueue.eta.EtaResult;
import com.smartqueue.repository.*;
import com.smartqueue.scheduling.QueueScheduler;
import com.smartqueue.util.BusinessSchedule;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** Read side of the queue: ordering, positions, ETA and DTO assembly. Never mutates queue state. */
@Service
public class QueueViewService {

    /** Computed once per queue and reused for every entry mapped from it. */
    public record Snapshot(ServiceQueue queue, QueuePolicy policy, List<QueueEntry> ordered, Map<Long, Integer> positions,
                           Map<Long, EtaResult> etas, EtaResult newJoinEta, List<QueueEntry> atCounter,
                           List<QueueEntry> skipped, boolean open, long sequence) {
        public int positionOf(Long entryId) {
            return positions.getOrDefault(entryId, 0);
        }
    }

    private final QueueEntryRepository entries;
    private final QueuePolicyRepository policies;
    private final CounterRepository counters;
    private final ServiceQueueRepository queues;
    private final BusinessScheduleService scheduleService;
    private final ServiceStatsService stats;
    private final QueueScheduler scheduler;
    private final EtaEstimator estimator;

    public QueueViewService(QueueEntryRepository entries, QueuePolicyRepository policies, CounterRepository counters,
                            ServiceQueueRepository queues, BusinessScheduleService scheduleService,
                            ServiceStatsService stats, QueueScheduler scheduler, EtaEstimator estimator) {
        this.entries = entries;
        this.policies = policies;
        this.counters = counters;
        this.queues = queues;
        this.scheduleService = scheduleService;
        this.stats = stats;
        this.scheduler = scheduler;
        this.estimator = estimator;
    }

    public QueuePolicy policyFor(Business b) {
        return policies.findByBusinessId(b.getId()).orElseGet(() -> QueuePolicy.defaults(b));
    }

    @Transactional(readOnly = true)
    public Snapshot snapshot(ServiceQueue q) {
        Instant now = Instant.now();
        Business b = q.getBusiness();
        BusinessService svc = q.getService();
        QueuePolicy policy = policyFor(b);

        List<QueueEntry> waiting = entries.findByQueueIdAndStatusOrderByQueueTimeAscIdAsc(q.getId(), EntryStatus.WAITING);
        List<QueueEntry> ordered = scheduler.order(waiting, policy, q.getConsecutiveHighPriorityServed(), now);
        Map<Long, Integer> positions = new HashMap<>();
        for (int i = 0; i < ordered.size(); i++) {
            positions.put(ordered.get(i).getId(), i + 1);
        }
        List<QueueEntry> atCounter = entries.findByQueueIdAndStatusInOrderByQueueTimeAscIdAsc(q.getId(), EntryStatus.AT_COUNTER);
        List<QueueEntry> skipped = entries.findByQueueIdAndStatusOrderByQueueTimeAscIdAsc(q.getId(), EntryStatus.SKIPPED);

        BusinessSchedule schedule = scheduleService.load(b);
        boolean open = schedule.isOpen(now);
        Instant closing = schedule.closingTime(now).orElse(null);
        List<Counter> serving = counters.findServingCounters(b.getId(), svc.getId(), CounterStatus.OPEN);
        ServiceStatsService.Stats st = stats.statsFor(svc.getId(), svc.getEstimatedDurationMinutes());

        List<EtaContext.Waiting> w = ordered.stream()
                .map(e -> new EtaContext.Waiting(e.getId(), e.getAppointment() == null ? null : e.getAppointment().getStartAt()))
                .toList();
        List<EtaContext.InProgress> inProgress = atCounter.stream()
                .map(e -> new EtaContext.InProgress(e.getServingStartedAt() != null ? e.getServingStartedAt()
                        : (e.getCalledAt() != null ? e.getCalledAt() : now)))
                .toList();
        EtaContext ctx = new EtaContext(now, w, inProgress, serving.size(), st.meanSeconds(), st.stdDevSeconds(),
                st.samples(), open, closing);
        return new Snapshot(q, policy, ordered, positions, estimator.estimateAll(ctx), estimator.estimateForNewJoin(ctx),
                atCounter, skipped, open, now.toEpochMilli());
    }

    // ---------------- mapping ----------------

    public QueueDto queueDto(ServiceQueue q) {
        long waiting = entries.countByQueueIdAndStatus(q.getId(), EntryStatus.WAITING);
        long atCounter = entries.countByQueueIdAndStatus(q.getId(), EntryStatus.CALLED)
                + entries.countByQueueIdAndStatus(q.getId(), EntryStatus.SERVING);
        return new QueueDto(q.getId(), q.getPublicId(), q.getName(), q.getService().getId(), q.getService().getName(),
                q.getStatus(), q.getTokenPrefix(), waiting, atCounter);
    }

    @Transactional(readOnly = true)
    public List<QueueDto> queueDtos(Long businessId) {
        return queues.findByBusinessIdOrderByNameAsc(businessId).stream().map(this::queueDto).toList();
    }

    public EntryDto staffEntry(QueueEntry e, Snapshot snap) {
        int pos = 0;
        EtaResult eta = null;
        if (snap != null && e.getStatus() == EntryStatus.WAITING) {
            pos = snap.positionOf(e.getId());
            eta = snap.etas().get(e.getId());
        }
        Counter c = e.getCounter();
        return new EntryDto(e.getId(), e.getPublicId(), e.getTokenLabel(), e.getTokenNumber(), e.displayName(),
                e.getGuestPhone() != null ? e.getGuestPhone() : (e.getCustomer() != null ? e.getCustomer().getPhone() : null),
                e.getCustomer() == null ? null : e.getCustomer().getId(), e.getStatus(), e.getSource(), e.getPriority(),
                e.getQueue().getId(), e.getQueue().getName(), e.getService().getName(),
                c == null ? null : c.getId(), c == null ? null : c.getName(), e.getJoinedAt(), e.getCalledAt(),
                e.getServingStartedAt(), e.getCompletedAt(), e.getRecallCount(), e.getSkipCount(), pos, eta,
                e.getAppointment() == null ? null : e.getAppointment().getStartAt(), e.getNote());
    }

    @Transactional(readOnly = true)
    public EntryDto staffEntry(QueueEntry e) {
        Snapshot snap = e.getStatus() == EntryStatus.WAITING ? snapshot(e.getQueue()) : null;
        return staffEntry(e, snap);
    }

    public EntryView customerView(QueueEntry e, Snapshot snap) {
        int pos = 0;
        EtaResult eta = null;
        if (snap != null && e.getStatus() == EntryStatus.WAITING) {
            pos = snap.positionOf(e.getId());
            eta = snap.etas().get(e.getId());
        }
        Counter c = e.getCounter();
        return new EntryView(e.getId(), e.getPublicId(), e.getTokenLabel(), e.getStatus(), e.getSource(), e.getPriority(),
                pos, Math.max(0, pos - 1), eta, e.getQueue().getPublicId(), e.getQueue().getName(),
                e.getService().getName(), e.getBusiness().getName(), e.getBusiness().getPublicId(),
                c == null ? null : c.getName(), e.getJoinedAt(), e.getCalledAt(),
                e.getAppointment() == null ? null : e.getAppointment().getStartAt(),
                snap == null ? Instant.now().toEpochMilli() : snap.sequence());
    }

    @Transactional(readOnly = true)
    public EntryView customerView(QueueEntry e) {
        Snapshot snap = e.getStatus() == EntryStatus.WAITING ? snapshot(e.getQueue()) : null;
        return customerView(e, snap);
    }

    @Transactional(readOnly = true)
    public StaffQueueDto staffView(Long businessId, Long queueId) {
        ServiceQueue q = queues.findByIdAndBusinessId(queueId, businessId)
                .orElseThrow(() -> com.smartqueue.common.ApiException.notFound("Queue not found"));
        Snapshot s = snapshot(q);
        return new StaffQueueDto(queueDto(q),
                s.ordered().stream().map(e -> staffEntry(e, s)).toList(),
                s.atCounter().stream().map(e -> staffEntry(e, s)).toList(),
                s.skipped().stream().map(e -> staffEntry(e, s)).toList(),
                s.sequence());
    }

    public PublicBoardDto board(ServiceQueue q, Snapshot s) {
        Business b = q.getBusiness();
        DisplayMode mode = b.getDisplayMode();
        boolean showCounter = mode != DisplayMode.ANONYMOUS_TOKEN;
        List<BoardItem> serving = s.atCounter().stream()
                .map(e -> new BoardItem(e.getTokenLabel(), showCounter && e.getCounter() != null ? e.getCounter().getName() : null,
                        shownName(mode, e.displayName())))
                .toList();
        List<BoardItem> next = s.ordered().stream().limit(5)
                .map(e -> new BoardItem(e.getTokenLabel(), null, shownName(mode, e.displayName())))
                .toList();
        Integer wait = (b.isShowWaitOnDisplay() && s.open()) ? s.newJoinEta().estimatedWaitMinutes() : null;
        return new PublicBoardDto(q.getPublicId(), q.getName(), q.getService().getName(), b.getName(), q.getStatus(),
                s.open(), mode, serving, next, s.ordered().size(), wait, b.isShowWaitOnDisplay(), Instant.now(), s.sequence());
    }

    @Transactional(readOnly = true)
    public PublicBoardDto board(ServiceQueue q) {
        return board(q, snapshot(q));
    }

    /** Privacy-aware name for public screens. */
    public static String shownName(DisplayMode mode, String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        String[] parts = fullName.trim().split("\\s+");
        return switch (mode) {
            case TOKEN_ONLY, ANONYMOUS_TOKEN -> null;
            case MASKED_NAME -> Arrays.stream(parts)
                    .map(p -> p.substring(0, 1) + "***").collect(Collectors.joining(" "));
            case FIRST_NAME_INITIAL -> parts.length == 1 ? parts[0]
                    : parts[0] + " " + parts[parts.length - 1].substring(0, 1).toUpperCase() + ".";
        };
    }
}
