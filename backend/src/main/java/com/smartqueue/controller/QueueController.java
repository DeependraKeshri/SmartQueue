package com.smartqueue.controller;

import com.smartqueue.dto.QueueDtos.*;
import com.smartqueue.service.QueueService;
import com.smartqueue.service.QueueViewService;
import java.util.List;
import java.util.Map;
import com.smartqueue.security.CurrentUser;
import com.smartqueue.security.Roles;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Staff-facing queue operations. The business is always taken from the authenticated principal. */
@RestController
@RequestMapping("/api/queues")
public class QueueController {
    private final QueueService queues;
    private final QueueViewService view;

    public QueueController(QueueService queues, QueueViewService view) {
        this.queues = queues;
        this.view = view;
    }

    @GetMapping
    @PreAuthorize(Roles.STAFF_UP)
    public List<QueueDto> list() {
        return view.queueDtos(CurrentUser.requireBusinessId());
    }

    @PostMapping
    @PreAuthorize(Roles.MANAGER_UP)
    @ResponseStatus(HttpStatus.CREATED)
    public QueueDto create(@Valid @RequestBody CreateQueueRequest req) {
        return queues.createQueue(CurrentUser.requireBusinessId(), req);
    }

    @GetMapping("/{id}")
    @PreAuthorize(Roles.STAFF_UP)
    public StaffQueueDto get(@PathVariable Long id) {
        return view.staffView(CurrentUser.requireBusinessId(), id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(Roles.MANAGER_UP)
    public QueueDto status(@PathVariable Long id, @Valid @RequestBody QueueStatusRequest req) {
        return queues.setQueueStatus(CurrentUser.requireBusinessId(), id, req);
    }

    @PostMapping("/{id}/walk-ins")
    @PreAuthorize(Roles.STAFF_UP)
    @ResponseStatus(HttpStatus.CREATED)
    public EntryDto walkIn(@PathVariable Long id, @Valid @RequestBody WalkInRequest req) {
        return queues.addWalkIn(CurrentUser.requireBusinessId(), id, req);
    }

    @PostMapping("/call-next")
    @PreAuthorize(Roles.STAFF_UP)
    public ResponseEntity<EntryDto> callNext(@Valid @RequestBody CallNextRequest req) {
        return queues.callNext(CurrentUser.requireBusinessId(), CurrentUser.require(), req)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{id}/announce-delay")
    @PreAuthorize(Roles.MANAGER_UP)
    public Map<String, Integer> announceDelay(@PathVariable Long id, @RequestParam(defaultValue = "15") int minutes,
                                              @RequestParam(required = false) String message) {
        int n = queues.announceDelay(CurrentUser.requireBusinessId(), id, Math.max(1, Math.min(minutes, 600)), message);
        return Map.of("notified", n);
    }
}
