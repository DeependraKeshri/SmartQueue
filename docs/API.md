# API summary (full interactive docs: `/swagger-ui.html`, `/v3/api-docs`)

Errors: `{timestamp,status,error,message,path,fieldErrors?}`. Auth: `Authorization: Bearer <jwt>`. Mutating customer/guest calls accept `Idempotency-Key` (≤ 64 chars).

| Area | Endpoints |
|---|---|
| Auth (public) | `POST /api/auth/register/customer`, `/register/business`, `/login` · `GET/PUT /api/users/me` |
| Public | `GET /api/public/businesses[?q]`, `/businesses/{pid}` · `GET /queues/{pid}/board` · `POST /queues/{pid}/join` (guest) · `GET/POST /entries/{pid}[/cancel]` · `GET /services/{id}/slots?date=` · `GET /qr/queues/{pid}.png`, `/qr/businesses/{pid}.png` |
| Customer | `GET /api/customer/entries[/{id}]` · `POST /customer/queues/{pid}/join` · `POST /customer/entries/{id}/cancel` · `GET/POST /customer/appointments` · `POST …/{id}/reschedule|cancel|check-in` |
| Staff | `GET /api/queues`, `/queues/{id}` · `POST /queues/{id}/walk-ins` · `POST /queues/call-next` · `POST /queue-entries/{id}/start|complete|skip|recall|no-show|move-to-end|cancel` · `PATCH …/priority` · `GET /api/appointments?date=` · `POST /appointments/{id}/confirm|check-in|complete|no-show|cancel` · `PATCH /counters/{id}/status` |
| Manager | `POST /queue-entries/{id}/call` (override) · `POST/PATCH /queues…` · `/api/services`, `/api/counters`, `/api/staff` CRUD · `PUT /api/business/hours`, breaks, holidays · analytics & insights |
| Admin | `PATCH /api/business` · `GET/PUT /api/business/policy` · `GET /api/audit-logs` |
| Analytics | `GET /api/analytics/daily|weekly|monthly?date=` · `/range?from&to` · `GET /api/insights/demand?date=` |
| Notifications | `GET /api/notifications`, `/unread-count`, `/preferences` · `POST /{id}/read`, `/read-all`, `/push-subscriptions` |
| Display | `GET /api/display/boards` (DISPLAY/staff of own business) |
| Platform | `GET /api/platform/businesses`, `PATCH /businesses/{id}/active` |
