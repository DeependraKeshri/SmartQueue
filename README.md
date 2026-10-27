# SmartQueue

Multi-tenant, real-time queue and appointment management platform
(Spring Boot 3 · PostgreSQL · Flyway · STOMP/WebSocket · optional Redis · React + Vite).

> **Status: delivered un-built.** This project was generated in an environment without Maven, npm or network access,
> so it has **not been compiled or run**. Expect to fix a few compile/test errors on first build (see "First run").
> All 16 phases of the specification are implemented in code; see `docs/PRODUCTION_REVIEW.md` for known limits.

## Layout
```
backend/    Spring Boot API  (src/main/java/com/smartqueue/..., Flyway migration, tests)
frontend/   React SPA        (Vite)
docs/       ARCHITECTURE, WEBSOCKET, SCHEDULING, API, PRODUCTION_REVIEW
docker-compose.yml, .env.example
```

## First run (development)
Requirements: JDK 21, Maven 3.9+, Node 20+, PostgreSQL 14+ (Redis optional).

```bash
# 1. database
createdb smartqueue            # user/password: smartqueue / smartqueue (see application-dev.yml)

# 2. backend (dev profile supplies a dev-only JWT secret; Flyway creates the schema)
cd backend
mvn clean verify               # compiles and runs all unit + integration + concurrency tests (H2, no DB needed)
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run     # http://localhost:8080  (Swagger: /swagger-ui.html)

# 3. frontend
cd ../frontend
npm install
npm run dev                    # http://localhost:5173 (proxies /api and /ws to :8080)
```

## Docker
```bash
cp .env.example .env           # fill DB_PASSWORD, REDIS_PASSWORD, JWT_SECRET
docker compose up --build      # app on http://localhost:8081
```

## Try it
1. **Register your business** (`/register/business`) → Admin → Services (create one; a live queue is created automatically) → Counters (create one, assign it to the service, then open it in the Counter screen) → Hours (defaults are Mon–Fri 09–17 in your timezone).
2. Open the service's QR/link, or `/businesses`, join as guest or customer, watch live position/ETA.
3. Staff console `/staff`: call next, start, complete, skip, recall, no-show, walk-ins, priorities.
4. `/display/<queuePublicId>` shows the waiting-room screen. Create a `DISPLAY` account under Admin → Staff for an authenticated all-queues screen.
5. A `PLATFORM_ADMIN` has no registration flow by design: insert one manually (BCrypt hash) into `users` with `role='PLATFORM_ADMIN'`.

## Roles
PLATFORM_ADMIN (cross-tenant business suspension) · BUSINESS_ADMIN (config, policy, audit, managers) · MANAGER (operations, analytics, staff) · STAFF (own counter, queue actions) · CUSTOMER · DISPLAY (display-safe data only).

## Key configuration (environment)
`JWT_SECRET` (required, ≥32 chars) · `DB_URL/DB_USERNAME/DB_PASSWORD` · `CORS_ALLOWED_ORIGINS` · `PUBLIC_BASE_URL` (used in QR codes) · `REDIS_ENABLED/REDIS_HOST/REDIS_PASSWORD` · `MAIL_ENABLED/MAIL_*` · `RATE_LIMIT_*` · `SWAGGER_ENABLED`.

## Documentation
`docs/ARCHITECTURE.md` (Phase 0 design) · `docs/SCHEDULING.md` · `docs/WEBSOCKET.md` · `docs/API.md` · `docs/PRODUCTION_REVIEW.md`.
