package com.smartqueue.controller;

import com.smartqueue.dto.AuthDtos.*;
import com.smartqueue.service.StaffService;
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
@RequestMapping("/api/staff")
public class StaffController {
    private final StaffService staff;

    public StaffController(StaffService staff) {
        this.staff = staff;
    }

    @GetMapping
    @PreAuthorize(Roles.MANAGER_UP)
    public List<UserDto> list() {
        return staff.list(CurrentUser.requireBusinessId());
    }

    @PostMapping
    @PreAuthorize(Roles.MANAGER_UP)
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@Valid @RequestBody CreateStaffRequest req) {
        CurrentUser.requireBusinessId();
        return staff.create(CurrentUser.require(), req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.MANAGER_UP)
    public UserDto update(@PathVariable Long id, @Valid @RequestBody UpdateStaffRequest req) {
        CurrentUser.requireBusinessId();
        return staff.update(CurrentUser.require(), id, req);
    }
}
