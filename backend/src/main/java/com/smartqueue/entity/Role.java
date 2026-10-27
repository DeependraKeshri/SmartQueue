package com.smartqueue.entity;

public enum Role {
    PLATFORM_ADMIN, BUSINESS_ADMIN, MANAGER, STAFF, CUSTOMER, DISPLAY;

    public boolean isBusinessStaff() {
        return this == BUSINESS_ADMIN || this == MANAGER || this == STAFF;
    }
}
