package com.smartqueue.controller;

import com.smartqueue.dto.BusinessDtos.*;
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
@RequestMapping("/api/services")
public class ServiceController {
    private final BusinessConfigService config;

    public ServiceController(BusinessConfigService config) {
        this.config = config;
    }

    @GetMapping
    @PreAuthorize(Roles.STAFF_UP)
    public List<ServiceDto> list() {
        return config.listServices(CurrentUser.requireBusinessId());
    }

    @PostMapping
    @PreAuthorize(Roles.MANAGER_UP)
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceDto create(@Valid @RequestBody ServiceRequest req) {
        return config.createService(CurrentUser.requireBusinessId(), req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.MANAGER_UP)
    public ServiceDto update(@PathVariable Long id, @Valid @RequestBody ServiceRequest req) {
        return config.updateService(CurrentUser.requireBusinessId(), id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.MANAGER_UP)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        config.deleteService(CurrentUser.requireBusinessId(), id);
    }
}
