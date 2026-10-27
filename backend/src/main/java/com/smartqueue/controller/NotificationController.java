package com.smartqueue.controller;

import com.smartqueue.dto.NotificationDtos.*;
import com.smartqueue.dto.PageDto;
import com.smartqueue.service.NotificationService;
import java.util.Map;
import com.smartqueue.security.CurrentUser;
import com.smartqueue.security.Roles;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping
    public PageDto<NotificationDto> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return notifications.list(CurrentUser.require().getId(), page, size);
    }

    @GetMapping("/unread-count")
    public UnreadCountDto unread() {
        return new UnreadCountDto(notifications.unread(CurrentUser.require().getId()));
    }

    @PostMapping("/{id}/read")
    public NotificationDto read(@PathVariable Long id) {
        return notifications.markRead(CurrentUser.require().getId(), id);
    }

    @PostMapping("/read-all")
    public Map<String, Integer> readAll() {
        return Map.of("updated", notifications.markAllRead(CurrentUser.require().getId()));
    }

    @GetMapping("/preferences")
    public PreferenceDto preferences() {
        return notifications.preferences(CurrentUser.require().getId());
    }

    @PutMapping("/preferences")
    public PreferenceDto updatePreferences(@Valid @RequestBody PreferenceDto req) {
        return notifications.updatePreferences(CurrentUser.require().getId(), req);
    }

    @PostMapping("/push-subscriptions")
    @ResponseStatus(HttpStatus.CREATED)
    public void registerPush(@Valid @RequestBody PushSubscriptionRequest req) {
        notifications.registerPush(CurrentUser.require().getId(), req);
    }
}
