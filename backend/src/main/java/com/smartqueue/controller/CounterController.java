package com.smartqueue.controller;

import com.smartqueue.dto.BusinessDtos.*;
import com.smartqueue.security.UserPrincipal;
import com.smartqueue.service.BusinessConfigService;
import java.util.List;
import com.smartqueue.security.CurrentUser;
import com.smartqueue.security.Roles;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/counters")
public class CounterController {
    private final BusinessConfigService config;

    public CounterController(BusinessConfigService config) {
        this.config = config;
    }

    @GetMapping
    @PreAuthorize(Roles.STAFF_UP)
    public List<CounterDto> list() {
        return config.listCounters(CurrentUser.requireBusinessId());
    }

    @PostMapping
    @PreAuthorize(Roles.MANAGER_UP)
    @ResponseStatus(HttpStatus.CREATED)
    public CounterDto create(@Valid @RequestBody CounterRequest req) {
        return config.createCounter(CurrentUser.requireBusinessId(), req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.MANAGER_UP)
    public CounterDto update(@PathVariable Long id, @Valid @RequestBody CounterRequest req) {
        return config.updateCounter(CurrentUser.requireBusinessId(), id, req);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(Roles.STAFF_UP)
    public CounterDto status(@PathVariable Long id, @Valid @RequestBody CounterStatusRequest req) {
        UserPrincipal u = CurrentUser.require();
        return config.setCounterStatus(CurrentUser.requireBusinessId(), id, req.status(),
                new BusinessConfigService.UserPrincipalView(u.getId(), u.getRole()));
    }
}
