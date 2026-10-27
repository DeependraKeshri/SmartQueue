package com.smartqueue.realtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Delivers events only after the originating transaction committed (never announces rolled-back changes). */
@Component
public class RealtimePublisher {
    private static final Logger log = LoggerFactory.getLogger(RealtimePublisher.class);
    private final QueueBroadcaster broadcaster;

    public RealtimePublisher(QueueBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onQueueChanged(QueueChangedEvent ev) {
        try {
            broadcaster.broadcast(ev);
        } catch (Exception e) {
            log.warn("Queue broadcast failed for queue {}: {}", ev.queueId(), e.getClass().getSimpleName());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAppointmentChanged(AppointmentChangedEvent ev) {
        try {
            broadcaster.broadcastAppointment(ev);
        } catch (Exception e) {
            log.warn("Appointment broadcast failed: {}", e.getClass().getSimpleName());
        }
    }
}
