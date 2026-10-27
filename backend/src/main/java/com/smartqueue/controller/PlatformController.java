package com.smartqueue.controller;

import com.smartqueue.dto.BusinessDtos.BusinessDto;
import com.smartqueue.service.PlatformService;
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
@RequestMapping("/api/platform")
@PreAuthorize(Roles.PLATFORM)
public class PlatformController {
    private final PlatformService platform;

    public PlatformController(PlatformService platform) {
        this.platform = platform;
    }

    @GetMapping("/businesses")
    public List<BusinessDto> list() {
        return platform.listBusinesses();
    }

    @PatchMapping("/businesses/{id}/active")
    public BusinessDto setActive(@PathVariable Long id, @RequestParam boolean active) {
        return platform.setActive(id, active);
    }
}
