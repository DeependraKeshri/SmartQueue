# WebSocket (STOMP) reference

Endpoint: `ws(s)://<host>/ws`. Heartbeat 10 s/10 s. Clients **cannot publish** (SEND is rejected); the server only pushes.
Authentication: optional `Authorization: Bearer <jwt>` STOMP CONNECT header. An invalid/expired token rejects the connection. Public pages connect anonymously.

| Destination | Who may subscribe | Payload |
|---|---|---|
| `/topic/public/queues/{queuePublicId}` | anyone | `PublicBoardDto` |
| `/topic/public/entries/{entryPublicId}` | anyone holding the unguessable entry UUID | `EntryView` |
| `/topic/business/{businessId}/queues` | staff/manager/admin **of that business**; DISPLAY accounts of that business | `StaffEvent` |
| `/topic/business/{businessId}/appointments` | staff/manager/admin of that business | `{type, appointmentId, sequence}` |
| `/user/queue/notifications` | any authenticated user (own only) | `NotificationDto` |

Any other destination is refused. Subscribing to another business' topic is refused.

## Payloads
* **PublicBoardDto** `{queuePublicId, queueName, serviceName, businessName, status, businessOpen, displayMode, nowServing:[{token,counter,displayName}], nextUp:[{token,displayName}], waitingCount, estimatedWaitMinutes, showWait, serverTime, sequence}` – names obey the business display mode (TOKEN_ONLY/ANONYMOUS → null, MASKED → `J*** D***`, FIRST_NAME_INITIAL → `John D.`). No phone numbers, ids of customers or emails.
* **EntryView** `{id, publicId, tokenLabel, status, source, priority, position, peopleAhead, eta:{peopleAhead, estimatedWaitMinutes, waitMinutesMin, waitMinutesMax, estimatedTurnTime, turnTimeMin, turnTimeMax, confidence, businessOpen, method, note}, queuePublicId, queueName, serviceName, businessName, counterName, joinedAt, calledAt, appointmentStartAt, serverSequence}`
* **StaffEvent** `{type, queueId, entryId, waitingCount, sequence}` where `type` ∈ JOINED, CALLED, SERVING, COMPLETED, SKIPPED, RECALLED, NO_SHOW, MOVED, PRIORITY, CANCELLED, QUEUE_STATUS. Staff clients refetch `GET /api/queues/{id}` (debounced) on each event.
* **Appointment event** `type` ∈ BOOKED, RESCHEDULED, CANCELLED, CONFIRMED, CHECKED_IN, NO_SHOW, COMPLETED.

## Delivery guarantees
Events are published **after commit** (never for rolled-back work). Every queue change recomputes order/ETA and re-sends the board plus each waiting customer's `EntryView` (capped at 300 per change). Delivery is at-most-once: after a reconnect the SPA re-fetches `GET /api/public/queues/{id}/board`, `GET /api/public/entries/{id}`, `GET /api/queues/{id}` – these are the last-known-state endpoints.
