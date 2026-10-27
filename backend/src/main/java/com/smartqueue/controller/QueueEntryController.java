package com.smartqueue.controller;

import com.smartqueue.dto.QueueDtos.*;
import com.smartqueue.service.QueueService;
import com.smartqueue.security.CurrentUser;
import com.smartqueue.security.Roles;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/queue-entries")
public class QueueEntryController {
    private final QueueService queues;

    public QueueEntryController(QueueService queues) {
        this.queues = queues;
    }

    @PostMapping("/{id}/start")
    @PreAuthorize(Roles.STAFF_UP)
    public EntryDto start(@PathVariable Long id) {
        return queues.start(CurrentUser.requireBusinessId(), id, CurrentUser.require());
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(Roles.STAFF_UP)
    public EntryDto complete(@PathVariable Long id) {
        return queues.complete(CurrentUser.requireBusinessId(), id, CurrentUser.require());
    }

    @PostMapping("/{id}/skip")
    @PreAuthorize(Roles.STAFF_UP)
    public EntryDto skip(@PathVariable Long id) {
        return queues.skip(CurrentUser.requireBusinessId(), id, CurrentUser.require());
    }

    @PostMapping("/{id}/recall")
    @PreAuthorize(Roles.STAFF_UP)
    public EntryDto recall(@PathVariable Long id, @RequestBody(required = false) CounterRequestBody body) {
        return queues.recall(CurrentUser.requireBusinessId(), id, CurrentUser.require(), body == null ? null : body.counterId());
    }

    @PostMapping("/{id}/no-show")
    @PreAuthorize(Roles.STAFF_UP)
    public EntryDto noShow(@PathVariable Long id) {
        return queues.markNoShow(CurrentUser.requireBusinessId(), id, CurrentUser.require());
    }

    @PostMapping("/{id}/move-to-end")
    @PreAuthorize(Roles.STAFF_UP)
    public EntryDto moveToEnd(@PathVariable Long id) {
        return queues.moveToEnd(CurrentUser.requireBusinessId(), id, CurrentUser.require());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(Roles.STAFF_UP)
    public EntryDto cancel(@PathVariable Long id) {
        return queues.cancelByStaff(CurrentUser.requireBusinessId(), id);
    }

    /** Manual override: call a specific customer out of order (manager/admin, reason mandatory, audited). */
    @PostMapping("/{id}/call")
    @PreAuthorize(Roles.MANAGER_UP)
    public EntryDto call(@PathVariable Long id, @Valid @RequestBody CallEntryRequest req) {
        return queues.callSpecific(CurrentUser.requireBusinessId(), id, CurrentUser.require(), req);
    }

    @PatchMapping("/{id}/priority")
    @PreAuthorize(Roles.STAFF_UP)
    public EntryDto priority(@PathVariable Long id, @Valid @RequestBody PriorityRequest req) {
        return queues.setPriority(CurrentUser.requireBusinessId(), id, req);
    }
}
