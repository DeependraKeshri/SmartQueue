package com.smartqueue.controller;

import com.smartqueue.dto.BusinessDtos.*;
import com.smartqueue.service.BusinessConfigService;
import com.smartqueue.security.CurrentUser;
import com.smartqueue.security.Roles;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/business")
public class BusinessController {
    private final BusinessConfigService config;

    public BusinessController(BusinessConfigService config) {
        this.config = config;
    }

    @GetMapping
    @PreAuthorize(Roles.STAFF_UP)
    public BusinessDto get() {
        return config.getBusiness(CurrentUser.requireBusinessId());
    }

    @PatchMapping
    @PreAuthorize(Roles.ADMIN)
    public BusinessDto update(@Valid @RequestBody UpdateBusinessRequest req) {
        return config.updateBusiness(CurrentUser.requireBusinessId(), req);
    }

    @GetMapping("/policy")
    @PreAuthorize(Roles.MANAGER_UP)
    public PolicyDto policy() {
        return config.getPolicy(CurrentUser.requireBusinessId());
    }

    @PutMapping("/policy")
    @PreAuthorize(Roles.ADMIN)
    public PolicyDto updatePolicy(@Valid @RequestBody PolicyDto req) {
        return config.updatePolicy(CurrentUser.requireBusinessId(), req);
    }

    @GetMapping("/schedule")
    @PreAuthorize(Roles.STAFF_UP)
    public ScheduleDto schedule() {
        return config.getSchedule(CurrentUser.requireBusinessId());
    }

    @PutMapping("/hours")
    @PreAuthorize(Roles.MANAGER_UP)
    public ScheduleDto hours(@Valid @RequestBody UpdateHoursRequest req) {
        return config.updateHours(CurrentUser.requireBusinessId(), req);
    }

    @PostMapping("/breaks")
    @PreAuthorize(Roles.MANAGER_UP)
    @ResponseStatus(HttpStatus.CREATED)
    public BreakDto addBreak(@Valid @RequestBody BreakDto req) {
        return config.addBreak(CurrentUser.requireBusinessId(), req);
    }

    @DeleteMapping("/breaks/{id}")
    @PreAuthorize(Roles.MANAGER_UP)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBreak(@PathVariable Long id) {
        config.deleteBreak(CurrentUser.requireBusinessId(), id);
    }

    @PostMapping("/holidays")
    @PreAuthorize(Roles.MANAGER_UP)
    @ResponseStatus(HttpStatus.CREATED)
    public HolidayDto addHoliday(@Valid @RequestBody HolidayDto req) {
        return config.addHoliday(CurrentUser.requireBusinessId(), req);
    }

    @DeleteMapping("/holidays/{id}")
    @PreAuthorize(Roles.MANAGER_UP)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteHoliday(@PathVariable Long id) {
        config.deleteHoliday(CurrentUser.requireBusinessId(), id);
    }
}
