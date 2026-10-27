package com.smartqueue.dto;

import com.smartqueue.entity.Role;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

public final class AuthDtos {
    private AuthDtos() {}

    public static final String PASSWORD_REGEX = "^(?=.*[A-Za-z])(?=.*\\d).{8,72}$";
    public static final String PASSWORD_MSG = "Password must be 8-72 characters and contain a letter and a digit";
    public static final String PHONE_REGEX = "^[0-9+()\\-\\s]{6,30}$";

    public record RegisterCustomerRequest(
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MSG) String password,
            @NotBlank @Size(max = 120) String fullName,
            @Pattern(regexp = PHONE_REGEX, message = "Invalid phone number") String phone) {}

    public record RegisterBusinessRequest(
            @NotBlank @Size(max = 120) String businessName,
            @Size(max = 64) String timezone,
            @Pattern(regexp = "^[A-Za-z]{1,5}$", message = "Token prefix must be 1-5 letters") String tokenPrefix,
            @NotBlank @Size(max = 120) String adminFullName,
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MSG) String password,
            @Pattern(regexp = PHONE_REGEX, message = "Invalid phone number") String phone) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank @Size(max = 72) String password) {}

    public record UserDto(Long id, String email, String fullName, String phone, Role role, Long businessId,
                          String businessName, UUID businessPublicId, boolean active, List<Long> serviceIds) {}

    public record AuthResponse(String token, String tokenType, long expiresInSeconds, UserDto user) {}

    public record UpdateProfileRequest(@NotBlank @Size(max = 120) String fullName,
                                       @Pattern(regexp = PHONE_REGEX, message = "Invalid phone number") String phone) {}

    public record CreateStaffRequest(
            @NotBlank @Email @Size(max = 190) String email,
            @NotBlank @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MSG) String password,
            @NotBlank @Size(max = 120) String fullName,
            @Pattern(regexp = PHONE_REGEX, message = "Invalid phone number") String phone,
            @NotNull Role role,
            List<Long> serviceIds) {}

    public record UpdateStaffRequest(@Size(max = 120) String fullName, String phone, Role role, Boolean active,
                                     List<Long> serviceIds) {}
}
