package com.smartqueue.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.cache.StateCache;
import com.smartqueue.config.AppProperties;
import com.smartqueue.dto.QueueDtos.PublicBoardDto;
import com.smartqueue.entity.*;
import com.smartqueue.repository.QueueEntryRepository;
import com.smartqueue.repository.ServiceQueueRepository;
import com.smartqueue.service.NotificationService;
import com.smartqueue.service.QueueViewService;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;

/**
 * Recomputes queue state after a committed change and pushes it to subscribers.
 * Every message carries a monotonically increasing {@code sequence} (server epoch millis); clients ignore older ones
 * and re-fetch the REST snapshot after a reconnect.
 */
@Service
public class QueueBroadcaster {
    private static final int MAX_ENTRY_PUSHES = 300;

    private final ServiceQueueRepository queues;
    private final QueueEntryRepository entries;
    private final QueueViewService view;
    private final SimpMessagingTemplate ws;
    private final StateCache cache;
    private final AppProperties props;
    private final ObjectMapper mapper;
    private final NotificationService notifications;

    public QueueBroadcaster(ServiceQueueRepository queues, QueueEntryRepository entries, QueueViewService view,
                            SimpMessagingTemplate ws, StateCache cache, AppProperties props, ObjectMapper mapper,
                            NotificationService notifications) {
        this.queues = queues;
        this.entries = entries;
        this.view = view;
        this.ws = ws;
        this.cache = cache;
        this.props = props;
        this.mapper = mapper;
        this.notifications = notifications;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void broadcast(QueueChangedEvent ev) {
        ServiceQueue q = queues.findById(ev.queueId()).orElse(null);
        if (q == null) {
            return;
        }
        QueueViewService.Snapshot snap = view.snapshot(q);
        PublicBoardDto board = view.board(q, snap);
        writeBoardCache(q.getId(), board);
        ws.convertAndSend("/topic/public/queues/" + q.getPublicId(), board);

        int pushed = 0;
        List<QueueEntry> live = new ArrayList<>(snap.ordered());
        live.addAll(snap.atCounter());
        Set<Long> sent = new HashSet<>();
        for (QueueEntry e : live) {
            if (pushed++ >= MAX_ENTRY_PUSHES) {
                break;
            }
            sent.add(e.getId());
            ws.convertAndSend("/topic/public/entries/" + e.getPublicId(), view.customerView(e, snap));
        }
        if (ev.entryId() != null && !sent.contains(ev.entryId())) {
            entries.findById(ev.entryId()).ifPresent(e ->
                    ws.convertAndSend("/topic/public/entries/" + e.getPublicId(), view.customerView(e, snap)));
        }
        ws.convertAndSend("/topic/business/" + ev.businessId() + "/queues",
                new StaffEvent(ev.reason(), q.getId(), ev.entryId(), snap.ordered().size(), snap.sequence()));

        notifyApproaching(snap);
    }

    private void notifyApproaching(QueueViewService.Snapshot snap) {
        int threshold = snap.policy().getApproachingThreshold();
        List<QueueEntry> ordered = snap.ordered();
        for (int i = 0; i < ordered.size() && i < threshold; i++) {
            QueueEntry e = ordered.get(i);
            if (e.getCustomer() != null && e.getApproachingNotifiedAt() == null) {
                e.setApproachingNotifiedAt(java.time.Instant.now());
                notifications.notifyUser(e.getCustomer().getId(), e.getBusiness().getId(), NotificationType.TURN_APPROACHING,
                        "Your turn is approaching",
                        "Token " + e.getTokenLabel() + " is number " + (i + 1) + " in line. Please be ready.",
                        Map.of("entryPublicId", e.getPublicId().toString(), "position", i + 1));
            }
        }
    }

    private void writeBoardCache(Long queueId, PublicBoardDto board) {
        try {
            cache.put("board:" + queueId, mapper.writeValueAsString(board), Duration.ofSeconds(props.getRedis().getBoardTtlSeconds()));
        } catch (JsonProcessingException ignored) {
            // cache is best-effort
        }
    }

    public Optional<PublicBoardDto> cachedBoard(Long queueId) {
        return cache.get("board:" + queueId).flatMap(json -> {
            try {
                return Optional.of(mapper.readValue(json, PublicBoardDto.class));
            } catch (Exception e) {
                return Optional.empty();
            }
        });
    }

    public void broadcastAppointment(AppointmentChangedEvent ev) {
        ws.convertAndSend("/topic/business/" + ev.businessId() + "/appointments",
                Map.of("type", ev.reason(), "appointmentId", ev.appointmentId(), "sequence", System.currentTimeMillis()));
    }
}
