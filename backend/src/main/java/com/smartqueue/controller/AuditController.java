package com.smartqueue.controller;

import com.smartqueue.audit.AuditService;
import com.smartqueue.dto.AuditDtos.AuditLogDto;
import com.smartqueue.dto.PageDto;
import java.time.Duration;
import java.time.Instant;
import com.smartqueue.security.CurrentUser;
import com.smartqueue.security.Roles;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/audit-logs")
public class AuditController {
    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    @PreAuthorize(Roles.ADMIN)
    public PageDto<AuditLogDto> search(@RequestParam(required = false) String action,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                       @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        Instant end = to == null ? Instant.now().plusSeconds(60) : to;
        Instant start = from == null ? end.minus(Duration.ofDays(30)) : from;
        return audit.search(CurrentUser.requireBusinessId(), action, start, end, page, size);
    }
}
