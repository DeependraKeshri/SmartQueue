package com.smartqueue.notification;

import com.smartqueue.entity.NotificationChannelType;

/** Delivery channel port (email, web push, later SMS...). In-app delivery is handled by NotificationService itself. */
public interface NotificationChannel {
    NotificationChannelType type();
    void send(OutboundMessage message);
}
