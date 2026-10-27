# SmartQueue – Architecture (Phase 0 deliverable)

Multi-tenant, real-time queue and appointment platform. Stack: **Spring Boot 3 / Java 21 / PostgreSQL / Flyway / Redis (optional) / STOMP over WebSocket** and **React 18 + Vite**.

## 1. System architecture
```
 Browser (React SPA) ──HTTPS──► nginx ──/api──► Spring Boot ──► PostgreSQL  (source of truth)
        │                         └──/ws───► (STOMP/WebSocket, in-process simple broker)
        └── QR/guest pages, public display, staff console, admin           └──► Redis (optional cache, rate limits)
                                                                           └──► SMTP (email) / web-push (extension point)
```
* **PostgreSQL** is the only source of truth. Redis and the WebSocket broker only hold derived data.
* The backend is a modular monolith: simple to deploy, transactional across queue + appointment + audit, split later along package lines if needed.

## 2. Frontend architecture (`frontend/src`)
* `api.js` axios client (JWT header, 401 handling, retry of GET on transient failure, error text extraction, idempotency-key helper).
* `auth.jsx` auth context; `realtime.js` STOMP hook (`useStomp`) with reconnect, sequence filtering and `onReconnect` resync.
* `components/` reusable pieces (`ui.jsx` helpers/hooks, `Nav`, `ErrorBoundary`); `pages/` route screens by audience: public, customer, staff, admin, display, platform.
* Every screen consumes the real API; no fixture data.

## 3. Backend architecture (`com.smartqueue`)
`controller` (HTTP, validation, `@PreAuthorize`) → `service` (transactions, rules, audit, events) → `repository` (Spring Data) → `entity`.
Supporting packages: `dto`, `security`, `config`, `common` (exceptions), `scheduling` (priority algorithm), `eta`, `analytics`, `intelligence` (demand), `notification`, `realtime`, `audit`, `cache`, `job`, `util`.
Controllers never contain business logic; services return DTOs (lazy entities never leave a transaction).

## 4. Database / entity design (Flyway `V1__initial_schema.sql`)
`businesses` (tenant root) · `users` (role, business) · `services` (soft delete) · `counters` + `counter_services` + `staff_services` · `business_hours`, `break_periods`, `holidays` · `queue_policies` · `queues` (token sequence, locked row) · `queue_entries` (history, never deleted) · `appointments` · `service_duration_records` (ETA/analytics history) · `notifications`, `notification_preferences`, `push_subscriptions` · `audit_logs` (append-only, DB trigger) · `daily_stats`, `hourly_stats`, `queue_length_samples` (aggregates).
All mutable tables have `created_at`, `updated_at`, optimistic `row_version`. Unique constraints enforce: one token number per queue per day, one queue per service, unique token prefix per business, idempotency keys, one booking key per customer.

## 5. Multi-tenant strategy
Shared schema, discriminator column (`business_id`). The tenant id is **never read from the request**: it comes from the authenticated principal (`CurrentUser.requireBusinessId()`), and every repository lookup is `findByIdAndBusinessId…`. A foreign id answers **404** (not 403) so existence is not leaked. Customers are not tenant-bound; they only reach rows keyed by their own user id. Public endpoints use unguessable UUIDs (`public_id`) and return no personal data.

## 6. Authentication & authorization
* BCrypt (cost 10) password hashing; generic "invalid email or password"; timing equalisation for unknown emails; 5 failures → 15-minute lock; rate limiting on `/api/auth/**`.
* JWT (HS256, secret ≥ 32 bytes from `JWT_SECRET`, 60 min). On every request the user is **re-loaded from the DB** (role, active flag, tenant active) so deactivation/role change is effective immediately.
* RBAC with method security: `STAFF_UP`, `MANAGER_UP`, `ADMIN`, `CUSTOMER`, `PLATFORM`. Staff can only operate counters assigned to them. DISPLAY accounts reach only `/api/display/boards` and queue-event topics.

## 7. Queue-engine design
`ServiceQueue` row = lock. Every state change: `SELECT … FOR UPDATE` on the queue (counter first, then queues in ascending id – deadlock-free), reload entities **after** the lock, validate with the `EntryStatus` state machine, mutate, audit, publish an event. Token = `prefix + 3-digit number`, sequence resets daily (business timezone). Statuses: WAITING → CALLED → SERVING → COMPLETED, plus SKIPPED / NO_SHOW / CANCELLED; timestamps recorded; nothing is deleted. Terminal operations are idempotent. See `SCHEDULING.md`.

## 8. Appointment-engine design
Slots are generated from business hours minus breaks/holidays, service duration and slot interval; capacity = number of active counters configured for the service (min 1). Booking/rescheduling/cancelling lock the **service row**, recompute overlaps under the lock, then write – so two customers can never take the last place. Check-in creates a live-queue entry with priority APPOINTMENT; the configurable policy decides how appointments and walk-ins interleave.

## 9. ETA design
`EtaEstimator` port; `StatisticalEtaEstimator` simulates counters: each open counter has a free-at time (remaining time of the current customer), waiting customers (in predicted service order, so priorities and appointment status are respected) are assigned greedily. Mean service time = Bayesian blend of the configured duration and the last 30 days of real durations. Output: peopleAhead, minutes (point + range), turn-time range, confidence (LOW/MEDIUM/HIGH from sample size and variability), business-open flag and notes ("may not be served before closing"). Closed business → no numeric estimate. A future ML model implements the same interface.

## 10. WebSocket architecture – see `WEBSOCKET.md`
## 11. Redis usage plan
Used only where it adds value and always optional (`REDIS_ENABLED`): (a) 5-second write-through cache of the public queue board (evicted/refreshed on every change); (b) 10-minute cache of demand forecasts (slow-changing); (c) distributed rate limiting. **Not cached:** live positions/ETAs for individuals, anything transactional. Redis failures degrade to cache-miss / fail-open limiter.

## 12. Notification architecture
`NotificationService.notifyUser` (inside the business transaction) → stores the in-app row → after commit pushes it over `/user/queue/notifications`, and hands email/push to a thread pool via `NotificationChannel` implementations. Preferences: per-user channel switches + muted types, plus business-level switches. Browser notifications are raised by the SPA from the live stream; Web-Push is an extension point (subscriptions stored).

## 13. Analytics architecture
Completed days are rolled up once into `daily_stats` / `hourly_stats` (lazily on first request and by an hourly job); "today" is computed live; per-service/counter/staff breakdowns are indexed range aggregates over `service_duration_records`; queue length is sampled every 5 minutes. Reports never scan raw history for past days.

## 14. Audit-log architecture
`AuditService.record` writes inside the caller's transaction (rolled-back actions leave no trail). Entity is `@Immutable`, repository exposes only save/read, and a PostgreSQL trigger rejects UPDATE/DELETE. Sensitive metadata keys are stripped. Tenant-scoped, ADMIN-only API.

## 15. Offline / reconnection strategy
STOMP auto-reconnect (3 s) with 10 s heartbeats; UI shows a banner and keeps the last known state; after reconnect every page re-fetches the REST snapshot (missed events are never replayed); messages carry a monotonic `sequence`, older ones are dropped; GETs auto-retry; mutations carry `Idempotency-Key`.

## 16. Testing strategy – `backend/src/test`
Unit: scheduler, ETA, stats, aggregation, schedule, demand, state machine, notification service. Integration (MockMvc + H2): auth, RBAC, tenant isolation, queue lifecycle, appointments, notifications, audit, analytics. Concurrency: 100 simultaneous joins, simultaneous call-next, same-slot booking, cancel-vs-advance race.

## 17. Deployment architecture
`docker-compose.yml`: postgres, redis, backend (non-root JRE image), frontend (nginx serving the SPA and proxying `/api` and `/ws`). Secrets via environment. Scale-out notes in `PRODUCTION_REVIEW.md` (the in-process broker must be replaced by an external STOMP relay before running >1 backend instance).

## 18. Implementation roadmap (all delivered in this repository)
1 foundation/DB → 2 auth/RBAC → 3 business config → 4 queue engine → 5 appointments → 6 QR/public display → 7 WebSocket → 8 ETA → 9 scheduling → 10 notifications/audit → 11 analytics → 12 Redis/demand → 13 reliability → 14 tests → 15 frontend → 16 hardening.
