package com.smartqueue.realtime;

public record StaffEvent(String type, Long queueId, Long entryId, long waitingCount, long sequence) {}
