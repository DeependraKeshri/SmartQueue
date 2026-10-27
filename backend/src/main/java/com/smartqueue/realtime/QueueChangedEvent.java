package com.smartqueue.realtime;

/** Published inside the mutating transaction; delivered to subscribers only AFTER the transaction commits. */
public record QueueChangedEvent(Long businessId, Long queueId, Long entryId, String reason) {}
