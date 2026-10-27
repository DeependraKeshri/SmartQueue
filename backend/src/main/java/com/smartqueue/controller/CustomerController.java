package com.smartqueue.controller;

import com.smartqueue.dto.QueueDtos.*;
import com.smartqueue.service.CustomerEntryService;
import com.smartqueue.service.QueueService;
import java.util.List;
import java.util.UUID;
import com.smartqueue.security.CurrentUser;
import com.smartqueue.security.Roles;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer")
@PreAuthorize(Roles.CUSTOMER)
public class CustomerController {
    private final QueueService queues;
    private final CustomerEntryService entries;

    public CustomerController(QueueService queues, CustomerEntryService entries) {
        this.queues = queues;
        this.entries = entries;
    }

    @GetMapping("/entries")
    public List<EntryView> myEntries() {
        return entries.recent(CurrentUser.require().getId());
    }

    @GetMapping("/entries/{id}")
    public EntryView entry(@PathVariable Long id) {
        return entries.get(CurrentUser.require().getId(), id);
    }

    @PostMapping("/queues/{queuePublicId}/join")
    public ResponseEntity<JoinResultDto> join(@PathVariable UUID queuePublicId,
                                              @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        JoinResultDto r = queues.joinAsCustomer(queuePublicId, CurrentUser.require().getId(), key);
        return ResponseEntity.status(r.duplicateRequest() ? HttpStatus.OK : HttpStatus.CREATED).body(r);
    }

    @PostMapping("/entries/{id}/cancel")
    public EntryView cancel(@PathVariable Long id) {
        return queues.cancelByCustomer(CurrentUser.require().getId(), id);
    }
}
