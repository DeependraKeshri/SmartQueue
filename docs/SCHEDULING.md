# Queue scheduling algorithm

Configured per business in `queue_policies` (Admin → Queue policy). Implemented as a pure function in `QueueScheduler`.

**Levels** (weights configurable): EMERGENCY 1000 · APPOINTMENT 500 · PRIORITY 200 · NORMAL 0.
**Effective level:** an APPOINTMENT entry only counts as APPOINTMENT between `start − earlyWindow` and `start + lateGrace` (and only if policy = `APPOINTMENT_PRIORITY`); outside that window (too early, late) it behaves as NORMAL and keeps its place by arrival time.
**Score:** `weight(level) + agingPointsPerMinute × minutesWaited` (waiting clock = `queueTime`, reset by "move to end").

**Next customer, in order:**
1. EMERGENCY entries (oldest first).
2. *Starvation guard*: anyone waiting ≥ `maxWaitMinutes` (longest wait first).
3. *Fairness*: after `fairnessEveryN` consecutive non-NORMAL calls the oldest NORMAL entry is served (emergencies do not advance the counter when `emergencyBypassesFairness`).
4. Highest score; ties → oldest `queueTime`, then id.

Positions and ETAs are produced by repeatedly applying this rule (`order`), so what customers see matches what staff will actually call. With several queues on one counter the best candidate across queues is chosen by score (approximation).
**Other rules:** no-show grace (`noShowGraceMinutes`, warning at half of it, automatic NO_SHOW if enabled), `maxRecalls`, skip (customer may be recalled or moved to end), manual override (`POST /queue-entries/{id}/call`, manager/admin, mandatory reason, audited as `QUEUE_MANUAL_OVERRIDE`; priority changes audited as `QUEUE_PRIORITY_OVERRIDE`).
Tests: `QueueSchedulerTest` (priority, normal, appointment+normal, multiple priorities, aging, fairness, starvation, ordering consistency).
