package com.smartqueue.security;

/** SpEL expressions for @PreAuthorize. */
public final class Roles {
    private Roles() {}
    public static final String ADMIN = "hasRole('BUSINESS_ADMIN')";
    public static final String MANAGER_UP = "hasAnyRole('BUSINESS_ADMIN','MANAGER')";
    public static final String STAFF_UP = "hasAnyRole('BUSINESS_ADMIN','MANAGER','STAFF')";
    public static final String CUSTOMER = "hasRole('CUSTOMER')";
    public static final String PLATFORM = "hasRole('PLATFORM_ADMIN')";
}
