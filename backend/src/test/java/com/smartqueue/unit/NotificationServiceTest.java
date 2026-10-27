package com.smartqueue.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartqueue.entity.*;
import com.smartqueue.notification.NotificationChannel;
import com.smartqueue.repository.*;
import com.smartqueue.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    private NotificationRepository notifications;
    private NotificationPreferenceRepository prefs;
    private AppUserRepository users;
    private BusinessRepository businesses;
    private SimpMessagingTemplate ws;
    private NotificationChannel email;
    private NotificationService service;

    @BeforeEach
    void setUp() {
        notifications = mock(NotificationRepository.class);
        prefs = mock(NotificationPreferenceRepository.class);
        users = mock(AppUserRepository.class);
        businesses = mock(BusinessRepository.class);
        ws = mock(SimpMessagingTemplate.class);
        email = mock(NotificationChannel.class);
        when(email.type()).thenReturn(NotificationChannelType.EMAIL);
        NotificationChannel push = mock(NotificationChannel.class);
        when(push.type()).thenReturn(NotificationChannelType.PUSH);
        service = new NotificationService(notifications, prefs, mock(PushSubscriptionRepository.class), users, businesses, ws,
                List.of(email, push), Runnable::run, new ObjectMapper());
        AppUser u = new AppUser();
        u.setId(7L);
        u.setEmail("a@b.c");
        u.setFullName("A B");
        u.setActive(true);
        when(users.findById(7L)).thenReturn(Optional.of(u));
        when(prefs.findByUserId(7L)).thenReturn(Optional.empty());
        Business b = new Business();
        b.setId(1L);
        when(businesses.findById(1L)).thenReturn(Optional.of(b));
    }

    @Test
    void defaultPreferencesStoreInAppAndPushRealtimeAndEmail() {
        service.notifyUser(7L, 1L, NotificationType.YOUR_TURN, "t", "m", Map.of());
        verify(notifications).save(any(Notification.class));
        verify(ws).convertAndSendToUser(eq("7"), eq("/queue/notifications"), any());
        verify(email).send(any());
    }

    @Test
    void mutedTypeProducesNothing() {
        NotificationPreference p = new NotificationPreference();
        p.setMutedTypes("YOUR_TURN");
        when(prefs.findByUserId(7L)).thenReturn(Optional.of(p));
        service.notifyUser(7L, 1L, NotificationType.YOUR_TURN, "t", "m", Map.of());
        verify(notifications, never()).save(any());
        verify(email, never()).send(any());
    }

    @Test
    void businessLevelSwitchOverridesUserPreference() {
        Business b = new Business();
        b.setId(1L);
        b.setEmailNotificationsEnabled(false);
        when(businesses.findById(1L)).thenReturn(Optional.of(b));
        service.notifyUser(7L, 1L, NotificationType.QUEUE_DELAY, "t", "m", Map.of());
        verify(notifications).save(any(Notification.class));
        verify(email, never()).send(any());
    }

    @Test
    void inactiveOrUnknownUsersAreIgnored() {
        service.notifyUser(999L, 1L, NotificationType.YOUR_TURN, "t", "m", Map.of());
        verify(notifications, never()).save(any());
    }
}
