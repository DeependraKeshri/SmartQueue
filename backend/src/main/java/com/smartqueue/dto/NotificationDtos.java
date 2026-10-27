package com.smartqueue.dto;

import com.smartqueue.entity.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class NotificationDtos {
    private NotificationDtos() {}

    public record NotificationDto(Long id, NotificationType type, String title, String message, String data,
                                  Instant readAt, Instant createdAt, Long businessId) {}

    public record PreferenceDto(boolean inAppEnabled, boolean emailEnabled, boolean pushEnabled, List<String> mutedTypes) {}

    public record PushSubscriptionRequest(@NotBlank @Size(max = 500) String endpoint, @Size(max = 200) String p256dh,
                                          @Size(max = 100) String auth) {}

    public record UnreadCountDto(long unread) {}
}
