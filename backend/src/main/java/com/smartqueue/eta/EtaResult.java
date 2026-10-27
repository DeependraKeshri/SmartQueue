package com.smartqueue.eta;

import java.time.Instant;

/** Estimated wait for a customer. Minutes are null when the business is closed (no honest estimate possible). */
public record EtaResult(int peopleAhead,
                        Integer estimatedWaitMinutes,
                        Integer waitMinutesMin,
                        Integer waitMinutesMax,
                        Instant estimatedTurnTime,
                        Instant turnTimeMin,
                        Instant turnTimeMax,
                        String confidence,
                        boolean businessOpen,
                        String method,
                        String note) {
}
