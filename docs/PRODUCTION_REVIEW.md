# Production-readiness review (Phase 16)

Status legend: **OK** = implemented in code · **NOTE** = known limitation / operator action.
**Important:** this code base was written without being compiled or executed in the authoring environment (no Maven / network). Run `./mvnw verify` and `npm run build` first and fix any compile/test findings before relying on the statements below.

| # | Area | Status |
|---|---|---|
| 1 | Authentication | OK: BCrypt, lockout, generic errors, rate limited. NOTE: no refresh tokens / MFA / password reset / email verification yet. |
| 2 | JWT | OK: HS256, ≥32-byte secret required at startup (no default in prod), expiry, user re-validated from DB on each request. NOTE: no token revocation list (deactivate the user to revoke). |
| 3 | Password security | OK: BCrypt(10), policy 8–72 chars letter+digit, never returned/logged. |
| 4 | RBAC | OK: method security per endpoint + staff-counter ownership. |
| 5 | Tenant isolation | OK: tenant from principal, `…AndBusinessId` lookups, 404 on foreign ids; tests in `SecurityIntegrationTest`. |
| 6 | Input validation | OK: Bean Validation on all DTOs, range checks, timezone/hours validation, idempotency-key length. |
| 7 | SQL injection | OK: JPA/JPQL with bound parameters only; no string-built SQL. |
| 8 | API authorization | OK: default `authenticated()`; explicit public list. |
| 9 | CORS | OK: explicit origins from `CORS_ALLOWED_ORIGINS`, no credentials, limited headers. |
| 10 | CSRF | OK/N/A: stateless bearer-token API, no cookies. NOTE: token is kept in `localStorage` (XSS exposure) – keep a strict CSP at nginx; consider httpOnly-cookie refresh flow. |
| 11 | Rate limiting | OK: `/api/auth/**` and public writes (per IP; Redis-backed when enabled). NOTE: behind a proxy set `server.forward-headers-strategy` (done) and trust only your proxy. |
| 12 | Error handling | OK: `GlobalExceptionHandler`, no stack traces/messages leaked, 409 for conflicts/races. |
| 13 | Logging | OK: no passwords/tokens/emails/message bodies logged; failures log exception class only. |
| 14 | Audit | OK: immutable, transactional, tenant-scoped. |
| 15 | Sensitive data exposure | OK: public payloads carry no PII; display privacy modes; guest phone only in staff DTOs. |
| 16 | Indexes | OK: see entity `@Index` and migration. NOTE: review `EXPLAIN` on real data volumes. |
| 17 | Transactions | OK: service-layer `@Transactional`; side effects (WebSocket, email) only after commit. |
| 18 | Concurrency | OK: pessimistic row locks with fixed lock order, unique constraints as safety net, optimistic `row_version`, idempotent terminal ops; concurrency tests included. |
| 19 | WebSocket security | OK: token auth, per-destination authorization, tenant check, publishing disabled, origin allow-list. NOTE: in-process simple broker → single backend instance, or use an external STOMP broker relay. |
| 20 | Redis | OK: optional, TTL'd, fail-open. NOTE: set `REDIS_PASSWORD`, keep Redis private. |
| 21 | Notification reliability | OK: after-commit, async, failures isolated. NOTE: no retry/outbox for email; web-push sender is an extension point. |
| 22 | API documentation | OK: springdoc (`SWAGGER_ENABLED=false` in production), `docs/API.md`. |
| 23 | Frontend error handling | OK: error boundary, per-request messages, stale-data banner, GET retry. |
| 24–25 | Env vars / secrets | OK: everything via environment (`.env.example`); no secrets committed; dev secret only in `application-dev.yml`. |
| 26 | Docker | OK: multi-stage builds, non-root backend, healthchecks. |
| 27 | Production config | OK: `ddl-auto=validate`, Flyway, swagger toggle, no error details. NOTE: add TLS at the proxy, and set `PUBLIC_BASE_URL`. |
| 28 | Migrations | OK: `V1__initial_schema.sql` mirrors the entities (validated at startup). |
| 29 | Automated tests | OK: see `docs/ARCHITECTURE.md` §16. NOTE: no browser/E2E and no WebSocket integration tests yet; no sustained load test (the 100-way concurrency test is functional, not a benchmark). |
| 30 | Build/deploy | OK: `mvn verify`, `npm run build`, `docker compose up --build`. |

## Known design limits worth knowing
* Appointment capacity is per service (number of counters assigned to it); two services sharing the same counters are not mutually exclusive.
* Multi-queue `call-next` across several queues uses highest score; strict policy rules are exact within one queue.
* Range median in analytics is a weighted average of daily medians (documented in the API response notes).
* Scheduled jobs run on every instance (idempotent, re-checked under lock).
* Demand forecasting is a transparent statistical model and deliberately refuses to forecast with < 3 days of history.
