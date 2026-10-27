package com.smartqueue.eta;

import java.util.Map;

/** Port so a different (e.g. ML based) estimator can replace the statistical one without touching callers. */
public interface EtaEstimator {
    /** ETA for each waiting entry, keyed by entry id. */
    Map<Long, EtaResult> estimateAll(EtaContext ctx);

    /** ETA for a customer who would join the queue right now (at the end). */
    EtaResult estimateForNewJoin(EtaContext ctx);
}
