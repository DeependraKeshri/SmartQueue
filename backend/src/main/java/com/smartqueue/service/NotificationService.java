package com.smartqueue.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.common.ApiException;
import com.smartqueue.dto.NotificationDtos.*;
import com.smartqueue.dto.PageDto;
import com.smartqueue.entity.*;
import com.smartqueue.notification.NotificationChannel;
import com.smartqueue.notification.OutboundMessage;
import com.smartqueue.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final NotificationPreferenceRepository preferences;
    private final PushSubscriptionRepository pushSubscriptions;
    private final AppUserRepository users;
    private final BusinessRepository businesses;
    private final SimpMessagingTemplate ws;
    private final Map<NotificationChannelType, NotificationChannel> channels = new EnumMap<>(NotificationChannelType.class);
    private final Executor executor;
    private final ObjectMapper mapper;

    public NotificationService(NotificationRepository notifications, NotificationPreferenceRepository preferences,
                               PushSubscriptionRepository pushSubscriptions, AppUserRepository users,
                               BusinessRepository businesses, SimpMessagingTemplate ws, List<NotificationChannel> channelList,
                               @Qualifier("notificationExecutor") Executor executor, ObjectMapper mapper) {
        this.notifications = notifications;
        this.preferences = preferences;
        this.pushSubscriptions = pushSubscriptions;
        this.users = users;
        this.businesses = businesses;
        this.ws = ws;
        channelList.forEach(c -> channels.put(c.type(), c));
        this.executor = executor;
        this.mapper = mapper;
    }

    /** Persists the in-app notification now; real-time/email/push delivery happens after the transaction commits. */
    @Transactional
    public void notifyUser(Long userId, Long businessId, NotificationType type, String title, String message,
                           Map<String, Object> data) {
        AppUser user = users.findById(userId).orElse(null);
        if (user == null || !user.isActive()) {
            return;
        }
        NotificationPreference pref = preferences.findByUserId(userId).orElseGet(NotificationPreference::new);
        if (pref.mutedSet().contains(type.name())) {
            return;
        }
        Business b = businessId == null ? null : businesses.findById(businessId).orElse(null);
        boolean inApp = pref.isInAppEnabled() && (b == null || b.isInAppNotificationsEnabled());
        boolean email = pref.isEmailEnabled() && (b == null || b.isEmailNotificationsEnabled());
        boolean push = pref.isPushEnabled() && (b == null || b.isPushNotificationsEnabled());

        if (inApp) {
            Notification n = new Notification();
            n.setUserId(userId);
            n.setBusinessId(businessId);
            n.setType(type);
            n.setTitle(truncate(title, 160));
            n.setMessage(truncate(message, 500));
            n.setData(toJson(data));
            notifications.save(n);
            NotificationDto dto = toDto(n);
            afterCommit(() -> ws.convertAndSendToUser(String.valueOf(userId), "/queue/notifications", dto));
        }
        OutboundMessage out = new OutboundMessage(userId, user.getEmail(), user.getFullName(), type, title, message);
        if (email) {
            afterCommit(() -> executor.execute(() -> channels.get(NotificationChannelType.EMAIL).send(out)));
        }
        if (push) {
            afterCommit(() -> executor.execute(() -> channels.get(NotificationChannelType.PUSH).send(out)));
        }
    }

    private void afterCommit(Runnable r) {
        Runnable safe = () -> {
            try {
                r.run();
            } catch (Exception e) {
                log.warn("Post-commit notification dispatch failed: {}", e.getClass().getSimpleName());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safe.run();
                }
            });
        } else {
            safe.run();
        }
    }

    private String toJson(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            return mapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    private NotificationDto toDto(Notification n) {
        return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getData(), n.getReadAt(),
                n.getCreatedAt(), n.getBusinessId());
    }

    @Transactional(readOnly = true)
    public PageDto<NotificationDto> list(Long userId, int page, int size) {
        var p = notifications.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100)));
        return PageDto.of(p, this::toDto);
    }

    @Transactional(readOnly = true)
    public long unread(Long userId) {
        return notifications.countByUserIdAndReadAtIsNull(userId);
    }

    @Transactional
    public NotificationDto markRead(Long userId, Long id) {
        Notification n = notifications.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Notification not found"));
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now());
        }
        return toDto(n);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return notifications.markAllRead(userId, Instant.now());
    }

    @Transactional(readOnly = true)
    public PreferenceDto preferences(Long userId) {
        NotificationPreference p = preferences.findByUserId(userId).orElseGet(NotificationPreference::new);
        return new PreferenceDto(p.isInAppEnabled(), p.isEmailEnabled(), p.isPushEnabled(), new ArrayList<>(new TreeSet<>(p.mutedSet())));
    }

    @Transactional
    public PreferenceDto updatePreferences(Long userId, PreferenceDto req) {
        NotificationPreference p = preferences.findByUserId(userId).orElseGet(() -> {
            NotificationPreference np = new NotificationPreference();
            np.setUserId(userId);
            return np;
        });
        p.setInAppEnabled(req.inAppEnabled());
        p.setEmailEnabled(req.emailEnabled());
        p.setPushEnabled(req.pushEnabled());
        Set<String> valid = Arrays.stream(NotificationType.values()).map(Enum::name).collect(Collectors.toSet());
        List<String> muted = req.mutedTypes() == null ? List.of() : req.mutedTypes().stream().filter(valid::contains).distinct().toList();
        p.setMutedTypes(String.join(",", muted));
        preferences.save(p);
        return preferences(userId);
    }

    @Transactional
    public void registerPush(Long userId, PushSubscriptionRequest req) {
        PushSubscription s = pushSubscriptions.findByEndpoint(req.endpoint()).orElseGet(PushSubscription::new);
        s.setUserId(userId);
        s.setEndpoint(req.endpoint());
        s.setP256dh(req.p256dh());
        s.setAuthKey(req.auth());
        pushSubscriptions.save(s);
    }
}
