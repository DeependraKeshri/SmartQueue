package com.smartqueue.controller;

import com.smartqueue.dto.AuthDtos.*;
import com.smartqueue.service.AuthService;
import com.smartqueue.security.CurrentUser;
import com.smartqueue.security.Roles;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final AuthService auth;

    public UserController(AuthService auth) {
        this.auth = auth;
    }

    @GetMapping("/me")
    public UserDto me() {
        return auth.me(CurrentUser.require().getId());
    }

    @PutMapping("/me")
    public UserDto update(@Valid @RequestBody UpdateProfileRequest req) {
        return auth.updateProfile(CurrentUser.require().getId(), req);
    }
}
