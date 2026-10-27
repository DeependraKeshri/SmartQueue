package com.smartqueue.notification;

import com.smartqueue.entity.NotificationChannelType;
import com.smartqueue.repository.PushSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Web-push extension point. Subscriptions are stored (POST /api/notifications/push-subscriptions).
 * To enable real delivery, add a VAPID web-push library and send the payload to each stored subscription here.
 */
@Component
public class PushNotificationChannel implements NotificationChannel {
    private static final Logger log = LoggerFactory.getLogger(PushNotificationChannel.class);
    private final PushSubscriptionRepository subscriptions;

    public PushNotificationChannel(PushSubscriptionRepository subscriptions) {
        this.subscriptions = subscriptions;
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.PUSH;
    }

    @Override
    public void send(OutboundMessage m) {
        int n = subscriptions.findByUserId(m.userId()).size();
        log.debug("Web push not configured; {} subscription(s) for user {} not notified", n, m.userId());
    }
}
