package com.smartqueue.notification;

import com.smartqueue.entity.NotificationType;

public record OutboundMessage(Long userId, String email, String name, NotificationType type, String title, String message) {}
