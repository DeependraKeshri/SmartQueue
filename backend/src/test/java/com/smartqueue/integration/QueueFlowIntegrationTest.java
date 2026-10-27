package com.smartqueue.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class QueueFlowIntegrationTest extends IntegrationTestBase {

    @Test
    void fullLifecycleJoinCallServeComplete() throws Exception {
        Biz biz = createBiz();
        String cust = customerToken();
        JsonNode joined = post("/api/customer/queues/" + biz.queuePublicId() + "/join", cust, null, 201);
        JsonNode entry = joined.get("entry");
        assertEquals("WAITING", entry.get("status").asText());
        assertEquals(1, entry.get("position").asInt());
        assertTrue(entry.get("tokenLabel").asText().endsWith("001"));
        assertNotNull(entry.get("eta"));

        JsonNode called = post("/api/queues/call-next", biz.adminToken(), Map.of("counterId", biz.counterId()), 200);
        assertEquals("CALLED", called.get("status").asText());
        long entryId = called.get("id").asLong();

        // counter busy until the customer is completed or skipped
        guestJoin(biz, "Second Guest");
        post("/api/queues/call-next", biz.adminToken(), Map.of("counterId", biz.counterId()), 409);

        assertEquals("SERVING", post("/api/queue-entries/" + entryId + "/start", biz.adminToken(), null, 200).get("status").asText());
        assertEquals("COMPLETED", post("/api/queue-entries/" + entryId + "/complete", biz.adminToken(), null, 200).get("status").asText());
        // idempotent complete
        assertEquals("COMPLETED", post("/api/queue-entries/" + entryId + "/complete", biz.adminToken(), null, 200).get("status").asText());

        JsonNode mine = get("/api/customer/entries", cust, 200);
        assertEquals("COMPLETED", mine.get(0).get("status").asText());
        // second customer can now be called
        post("/api/queues/call-next", biz.adminToken(), Map.of("counterId", biz.counterId()), 200);
    }

    @Test
    void duplicateJoinIsRejectedAndIdempotencyKeyReturnsSameToken() throws Exception {
        Biz biz = createBiz();
        String cust = customerToken();
        String key = "key-" + uid();
        MvcResult first = callRaw(MockMvcRequestBuilders.post("/api/customer/queues/" + biz.queuePublicId() + "/join")
                .header("Idempotency-Key", key), cust, null);
        MvcResult second = callRaw(MockMvcRequestBuilders.post("/api/customer/queues/" + biz.queuePublicId() + "/join")
                .header("Idempotency-Key", key), cust, null);
        assertEquals(201, first.getResponse().getStatus());
        assertEquals(200, second.getResponse().getStatus());
        JsonNode a = om.readTree(first.getResponse().getContentAsString());
        JsonNode b = om.readTree(second.getResponse().getContentAsString());
        assertEquals(a.get("entry").get("id").asLong(), b.get("entry").get("id").asLong());
        assertTrue(b.get("duplicateRequest").asBoolean());
        // a different request while already in the queue conflicts
        post("/api/customer/queues/" + biz.queuePublicId() + "/join", cust, null, 409);
    }

    @Test
    void cancelIsIdempotentAndFreesPosition() throws Exception {
        Biz biz = createBiz();
        JsonNode first = guestJoin(biz, "First Guest").get("entry");
        JsonNode second = guestJoin(biz, "Second Guest").get("entry");
        assertEquals(2, second.get("position").asInt());
        post("/api/public/entries/" + first.get("publicId").asText() + "/cancel", null, null, 200);
        post("/api/public/entries/" + first.get("publicId").asText() + "/cancel", null, null, 200);
        JsonNode now = get("/api/public/entries/" + second.get("publicId").asText(), null, 200);
        assertEquals(1, now.get("position").asInt());
    }

    @Test
    void priorityEntriesAreCalledFirstAndOverridesAreAudited() throws Exception {
        Biz biz = createBiz();
        guestJoin(biz, "Normal One");
        JsonNode emergency = post("/api/queues/" + biz.queueId() + "/walk-ins", biz.adminToken(),
                Map.of("guestName", "Urgent Case", "priority", "EMERGENCY"), 201);
        JsonNode called = post("/api/queues/call-next", biz.adminToken(), Map.of("counterId", biz.counterId()), 200);
        assertEquals(emergency.get("id").asLong(), called.get("id").asLong());

        JsonNode audit = get("/api/audit-logs?action=QUEUE_CALL_NEXT", biz.adminToken(), 200);
        assertTrue(audit.get("totalElements").asInt() >= 1);
        // manual override requires a reason
        JsonNode other = guestJoin(biz, "Other Person").get("entry");
        post("/api/queue-entries/" + called.get("id").asLong() + "/complete", biz.adminToken(), null, 200);
        post("/api/queue-entries/" + other.get("id").asLong() + "/call", biz.adminToken(), Map.of("counterId", biz.counterId()), 400);
        post("/api/queue-entries/" + other.get("id").asLong() + "/call", biz.adminToken(),
                Map.of("counterId", biz.counterId(), "reason", "VIP request"), 200);
        assertTrue(get("/api/audit-logs?action=QUEUE_MANUAL_OVERRIDE", biz.adminToken(), 200).get("totalElements").asInt() >= 1);
    }

    @Test
    void skipRecallNoShowAndMoveToEnd() throws Exception {
        Biz biz = createBiz();
        JsonNode a = guestJoin(biz, "Aaa Aaa").get("entry");
        guestJoin(biz, "Bbb Bbb");
        long id = post("/api/queues/call-next", biz.adminToken(), Map.of("counterId", biz.counterId()), 200).get("id").asLong();
        assertEquals(a.get("id").asLong(), id);
        assertEquals("SKIPPED", post("/api/queue-entries/" + id + "/skip", biz.adminToken(), null, 200).get("status").asText());
        // recall the skipped customer at the counter
        assertEquals("CALLED", post("/api/queue-entries/" + id + "/recall", biz.adminToken(), Map.of("counterId", biz.counterId()), 200)
                .get("status").asText());
        assertEquals("WAITING", post("/api/queue-entries/" + id + "/move-to-end", biz.adminToken(), null, 200).get("status").asText());
        // B is now first
        JsonNode next = post("/api/queues/call-next", biz.adminToken(), Map.of("counterId", biz.counterId()), 200);
        assertNotEquals(id, next.get("id").asLong());
        assertEquals("NO_SHOW", post("/api/queue-entries/" + next.get("id").asLong() + "/no-show", biz.adminToken(), null, 200).get("status").asText());
        // illegal transition
        post("/api/queue-entries/" + next.get("id").asLong() + "/start", biz.adminToken(), null, 409);
    }

    @Test
    void closedQueueAndPausedQueueRefuseJoins() throws Exception {
        Biz biz = createBiz();
        patch("/api/queues/" + biz.queueId() + "/status", biz.adminToken(), Map.of("status", "PAUSED"), 200);
        post("/api/public/queues/" + biz.queuePublicId() + "/join", null, Map.of("guestName", "Late Guest"), 409);
        patch("/api/queues/" + biz.queueId() + "/status", biz.adminToken(), Map.of("status", "ACTIVE"), 200);
        guestJoin(biz, "Now Fine");
        // deactivating with waiting customers needs explicit confirmation
        patch("/api/queues/" + biz.queueId() + "/status", biz.adminToken(), Map.of("status", "INACTIVE"), 409);
        patch("/api/queues/" + biz.queueId() + "/status", biz.adminToken(), Map.of("status", "INACTIVE", "cancelWaiting", true), 200);
    }

    @Test
    void publicBoardNeverLeaksNamesInTokenOnlyMode() throws Exception {
        Biz biz = createBiz();
        patch("/api/business", biz.adminToken(), Map.of("displayMode", "TOKEN_ONLY"), 200);
        guestJoin(biz, "Secret Person");
        JsonNode board = get("/api/public/queues/" + biz.queuePublicId() + "/board", null, 200);
        assertFalse(board.toString().contains("Secret"));
        assertEquals(1, board.get("waitingCount").asInt());
        patch("/api/business", biz.adminToken(), Map.of("displayMode", "FIRST_NAME_INITIAL"), 200);
        JsonNode board2 = get("/api/public/queues/" + biz.queuePublicId() + "/board", null, 200);
        assertTrue(board2.toString().contains("Secret P."));
        assertFalse(board2.toString().contains("phone"));
    }

    @Test
    void qrCodeIsAPng() throws Exception {
        Biz biz = createBiz();
        MvcResult r = mvc.perform(MockMvcRequestBuilders.get("/api/public/qr/queues/" + biz.queuePublicId() + ".png")).andReturn();
        assertEquals(200, r.getResponse().getStatus());
        byte[] png = r.getResponse().getContentAsByteArray();
        assertTrue(png.length > 100);
        assertEquals((byte) 0x89, png[0]);
    }

    @Test
    void completedServiceFeedsStatistics() throws Exception {
        Biz biz = createBiz();
        guestJoin(biz, "Stat Guest");
        long id = post("/api/queues/call-next", biz.adminToken(), Map.of("counterId", biz.counterId()), 200).get("id").asLong();
        post("/api/queue-entries/" + id + "/start", biz.adminToken(), null, 200);
        post("/api/queue-entries/" + id + "/complete", biz.adminToken(), null, 200);
        JsonNode daily = get("/api/analytics/daily", biz.adminToken(), 200);
        assertEquals(1, daily.get("completed").asInt());
        assertEquals(1, daily.get("totalCustomers").asInt());
        assertEquals(1, daily.get("services").size());
        assertEquals(1, daily.get("counters").get(0).get("served").asInt());
        JsonNode forecast = get("/api/insights/demand", biz.adminToken(), 200);
        assertFalse(forecast.get("sufficientData").asBoolean());   // no history yet: honest answer instead of invented numbers
    }
}
