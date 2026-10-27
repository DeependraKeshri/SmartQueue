package com.smartqueue.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PrivacyAndNotificationIntegrationTest extends IntegrationTestBase {

    @Test
    void customerCannotSeeOrCancelAnotherCustomersData() throws Exception {
        Biz biz = createBiz();
        String a = customerToken();
        String b = customerToken();
        long entryOfA = post("/api/customer/queues/" + biz.queuePublicId() + "/join", a, null, 201).get("entry").get("id").asLong();
        get("/api/customer/entries/" + entryOfA, b, 404);
        post("/api/customer/entries/" + entryOfA + "/cancel", b, null, 404);
        assertEquals(0, get("/api/customer/entries", b, 200).size());
        // customers never reach staff data
        get("/api/queues/" + biz.queueId(), b, 403);

        // appointments are private as well
        String date = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString();
        JsonNode slots = get("/api/public/services/" + biz.serviceId() + "/slots?date=" + date, null, 200);
        long apptOfA = post("/api/customer/appointments", a, Map.of("serviceId", biz.serviceId(), "startAt", slots.get(5).get("startAt").asText()), 201).get("id").asLong();
        post("/api/customer/appointments/" + apptOfA + "/cancel", b, Map.of(), 404);
        post("/api/customer/appointments/" + apptOfA + "/reschedule", b, Map.of("startAt", slots.get(6).get("startAt").asText()), 404);
        assertEquals(0, get("/api/customer/appointments", b, 200).size());
    }

    @Test
    void displayAccountsOnlySeeDisplaySafeData() throws Exception {
        Biz biz = createBiz();
        String email = "display" + uid() + "@test.io";
        post("/api/staff", biz.adminToken(), Map.of("email", email, "password", "Passw0rdTest", "fullName", "Lobby Screen", "role", "DISPLAY"), 201);
        String display = post("/api/auth/login", null, Map.of("email", email, "password", "Passw0rdTest"), 200).get("token").asText();
        guestJoin(biz, "Hidden Person");
        JsonNode boards = get("/api/display/boards", display, 200);
        assertEquals(1, boards.size());
        get("/api/queues", display, 403);
        get("/api/queues/" + biz.queueId(), display, 403);
        get("/api/staff", display, 403);
        get("/api/analytics/daily", display, 403);
        get("/api/audit-logs", display, 403);
        post("/api/queues/call-next", display, Map.of("counterId", biz.counterId()), 403);
        get("/api/customer/entries", display, 403);
    }

    @Test
    void notificationsAreDeliveredToTheCustomerAndRespectPreferences() throws Exception {
        Biz biz = createBiz();
        String cust = customerToken();
        long entryId = post("/api/customer/queues/" + biz.queuePublicId() + "/join", cust, null, 201).get("entry").get("id").asLong();
        assertEquals(1, get("/api/notifications/unread-count", cust, 200).get("unread").asInt());     // QUEUE_JOINED

        post("/api/queues/call-next", biz.adminToken(), Map.of("counterId", biz.counterId()), 200);
        JsonNode inbox = get("/api/notifications", cust, 200);
        List<String> types = new java.util.ArrayList<>();
        inbox.get("items").forEach(n -> types.add(n.get("type").asText()));
        assertTrue(types.contains("QUEUE_JOINED"));
        assertTrue(types.contains("YOUR_TURN"));

        post("/api/notifications/read-all", cust, null, 200);
        assertEquals(0, get("/api/notifications/unread-count", cust, 200).get("unread").asInt());

        // mute YOUR_TURN and verify a recall produces no new notification of that type
        put("/api/notifications/preferences", cust, Map.of("inAppEnabled", true, "emailEnabled", false, "pushEnabled", false,
                "mutedTypes", List.of("YOUR_TURN")), 200);
        post("/api/queue-entries/" + entryId + "/recall", biz.adminToken(), null, 200);
        assertEquals(0, get("/api/notifications/unread-count", cust, 200).get("unread").asInt());

        // in-app disabled entirely
        put("/api/notifications/preferences", cust, Map.of("inAppEnabled", false, "emailEnabled", false, "pushEnabled", false,
                "mutedTypes", List.of()), 200);
        post("/api/queue-entries/" + entryId + "/recall", biz.adminToken(), null, 200);
        assertEquals(0, get("/api/notifications/unread-count", cust, 200).get("unread").asInt());
    }

    @Test
    void businessCancellationAndDelayNotificationsReachWaitingCustomers() throws Exception {
        Biz biz = createBiz();
        String cust = customerToken();
        post("/api/customer/queues/" + biz.queuePublicId() + "/join", cust, null, 201);
        post("/api/notifications/read-all", cust, null, 200);
        JsonNode r = post("/api/queues/" + biz.queueId() + "/announce-delay?minutes=20", biz.adminToken(), null, 200);
        assertEquals(1, r.get("notified").asInt());
        patch("/api/queues/" + biz.queueId() + "/status", biz.adminToken(), Map.of("status", "INACTIVE", "cancelWaiting", true), 200);
        JsonNode inbox = get("/api/notifications", cust, 200);
        List<String> types = new java.util.ArrayList<>();
        inbox.get("items").forEach(n -> types.add(n.get("type").asText()));
        assertTrue(types.contains("QUEUE_DELAY"));
        assertTrue(types.contains("BUSINESS_CANCELLATION"));
    }

    @Test
    void auditLogIsTenantIsolatedAndAdminOnly() throws Exception {
        Biz a = createBiz();
        Biz b = createBiz();
        guestJoin(a, "Audited Guest");
        JsonNode logsA = get("/api/audit-logs", a.adminToken(), 200);
        JsonNode logsB = get("/api/audit-logs", b.adminToken(), 200);
        boolean aHasJoin = false;
        for (JsonNode l : logsA.get("items")) if (l.get("action").asText().equals("QUEUE_JOINED")) aHasJoin = true;
        assertTrue(aHasJoin);
        for (JsonNode l : logsB.get("items")) assertNotEquals("QUEUE_JOINED", l.get("action").asText());
        // no secrets in audit metadata
        assertFalse(logsA.toString().toLowerCase().contains("passw0rd"));
    }
}
