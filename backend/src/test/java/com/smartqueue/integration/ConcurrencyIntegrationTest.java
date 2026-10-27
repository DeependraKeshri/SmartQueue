package com.smartqueue.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ConcurrencyIntegrationTest extends IntegrationTestBase {

    private <T> List<T> runParallel(int threads, List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            CountDownLatch go = new CountDownLatch(1);
            List<Future<T>> futures = new ArrayList<>();
            for (Callable<T> t : tasks) {
                futures.add(pool.submit(() -> {
                    go.await();
                    return t.call();
                }));
            }
            go.countDown();
            List<T> out = new ArrayList<>();
            for (Future<T> f : futures) out.add(f.get(120, TimeUnit.SECONDS));
            return out;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void hundredSimultaneousJoinsGetUniqueSequentialTokens() throws Exception {
        Biz biz = createBiz();
        List<Callable<MvcResult>> tasks = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            final int n = i;
            tasks.add(() -> callRaw(MockMvcRequestBuilders.post("/api/public/queues/" + biz.queuePublicId() + "/join"), null,
                    Map.of("guestName", "Guest " + n)));
        }
        List<MvcResult> results = runParallel(20, tasks);
        Set<Integer> numbers = new TreeSet<>();
        for (MvcResult r : results) {
            assertEquals(201, r.getResponse().getStatus(), r.getResponse().getContentAsString());
            String label = om.readTree(r.getResponse().getContentAsString()).get("entry").get("tokenLabel").asText();
            numbers.add(Integer.parseInt(label.substring(label.length() - 3)));
        }
        assertEquals(100, numbers.size(), "every token must be unique");
        assertEquals(1, ((TreeSet<Integer>) numbers).first());
        assertEquals(100, ((TreeSet<Integer>) numbers).last());
    }

    @Test
    void concurrentCallNextNeverServesTheSameCustomerTwice() throws Exception {
        Biz biz = createBiz(3);
        for (int i = 0; i < 6; i++) guestJoin(biz, "Guest " + i);
        JsonNode counters = get("/api/counters", biz.adminToken(), 200);
        List<Callable<MvcResult>> tasks = new ArrayList<>();
        for (JsonNode c : counters) {
            final long cid = c.get("id").asLong();
            tasks.add(() -> callRaw(MockMvcRequestBuilders.post("/api/queues/call-next"), biz.adminToken(), Map.of("counterId", cid)));
        }
        List<MvcResult> results = runParallel(3, tasks);
        Set<Long> called = new HashSet<>();
        for (MvcResult r : results) {
            assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
            assertTrue(called.add(om.readTree(r.getResponse().getContentAsString()).get("id").asLong()), "entry called twice");
        }
        assertEquals(3, called.size());
    }

    @Test
    void sameCounterCannotBeGivenTwoCustomersAtOnce() throws Exception {
        Biz biz = createBiz();
        for (int i = 0; i < 4; i++) guestJoin(biz, "Guest " + i);
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tasks.add(() -> callRaw(MockMvcRequestBuilders.post("/api/queues/call-next"), biz.adminToken(),
                    Map.of("counterId", biz.counterId())).getResponse().getStatus());
        }
        List<Integer> statuses = runParallel(4, tasks);
        assertEquals(1, statuses.stream().filter(s -> s == 200).count());
        assertEquals(3, statuses.stream().filter(s -> s == 409).count());
    }

    @Test
    void lastAppointmentSlotCanOnlyBeBookedOnce() throws Exception {
        Biz biz = createBiz();   // one counter => capacity 1
        String date = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString();
        JsonNode slots = get("/api/public/services/" + biz.serviceId() + "/slots?date=" + date, null, 200);
        assertTrue(slots.size() > 10);
        String start = slots.get(10).get("startAt").asText();
        assertEquals(1, slots.get(10).get("capacity").asInt());

        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < 8; i++) tokens.add(customerToken());
        List<Callable<Integer>> tasks = new ArrayList<>();
        for (String t : tokens) {
            tasks.add(() -> callRaw(MockMvcRequestBuilders.post("/api/customer/appointments"), t,
                    Map.of("serviceId", biz.serviceId(), "startAt", start)).getResponse().getStatus());
        }
        List<Integer> statuses = runParallel(8, tasks);
        assertEquals(1, statuses.stream().filter(s -> s == 201).count(), statuses.toString());
        assertEquals(7, statuses.stream().filter(s -> s == 409).count(), statuses.toString());
        JsonNode after = get("/api/public/services/" + biz.serviceId() + "/slots?date=" + date, null, 200);
        assertEquals(0, after.get(10).get("remaining").asInt());
    }

    @Test
    void appointmentBookingIsIdempotentAndRescheduleFreesTheOldSlot() throws Exception {
        Biz biz = createBiz();
        String cust = customerToken();
        String date = LocalDate.now(ZoneOffset.UTC).plusDays(1).toString();
        JsonNode slots = get("/api/public/services/" + biz.serviceId() + "/slots?date=" + date, null, 200);
        String s1 = slots.get(20).get("startAt").asText();
        String s2 = slots.get(30).get("startAt").asText();
        String key = "k" + uid();
        MvcResult a = callRaw(MockMvcRequestBuilders.post("/api/customer/appointments").header("Idempotency-Key", key), cust,
                Map.of("serviceId", biz.serviceId(), "startAt", s1));
        MvcResult b = callRaw(MockMvcRequestBuilders.post("/api/customer/appointments").header("Idempotency-Key", key), cust,
                Map.of("serviceId", biz.serviceId(), "startAt", s1));
        assertEquals(201, a.getResponse().getStatus());
        assertEquals(201, b.getResponse().getStatus());
        long id = om.readTree(a.getResponse().getContentAsString()).get("id").asLong();
        assertEquals(id, om.readTree(b.getResponse().getContentAsString()).get("id").asLong());
        assertEquals(1, get("/api/customer/appointments", cust, 200).size());

        post("/api/customer/appointments/" + id + "/reschedule", cust, Map.of("startAt", s2), 200);
        JsonNode after = get("/api/public/services/" + biz.serviceId() + "/slots?date=" + date, null, 200);
        assertEquals(1, after.get(20).get("remaining").asInt());
        assertEquals(0, after.get(30).get("remaining").asInt());
        // cancel is idempotent and frees the slot
        post("/api/customer/appointments/" + id + "/cancel", cust, Map.of(), 200);
        post("/api/customer/appointments/" + id + "/cancel", cust, Map.of(), 200);
        assertEquals(1, get("/api/public/services/" + biz.serviceId() + "/slots?date=" + date, null, 200).get(30).get("remaining").asInt());
    }

    @Test
    void cancelRacingWithCallNextLeavesAConsistentState() throws Exception {
        Biz biz = createBiz();
        JsonNode entry = guestJoin(biz, "Racer One").get("entry");
        String publicId = entry.get("publicId").asText();
        List<Callable<Integer>> tasks = List.of(
                () -> callRaw(MockMvcRequestBuilders.post("/api/public/entries/" + publicId + "/cancel"), null, null).getResponse().getStatus(),
                () -> callRaw(MockMvcRequestBuilders.post("/api/queues/call-next"), biz.adminToken(),
                        Map.of("counterId", biz.counterId())).getResponse().getStatus());
        List<Integer> statuses = runParallel(2, tasks);
        for (int s : statuses) assertTrue(Set.of(200, 204, 409).contains(s), statuses.toString());
        String finalStatus = get("/api/public/entries/" + publicId, null, 200).get("status").asText();
        assertTrue(Set.of("CANCELLED", "CALLED").contains(finalStatus), finalStatus);
        if (finalStatus.equals("CANCELLED")) {
            // the counter must not be stuck on a cancelled customer
            guestJoin(biz, "Racer Two");
            post("/api/queues/call-next", biz.adminToken(), Map.of("counterId", biz.counterId()), 200);
        }
    }

    @Test
    void appointmentCheckInCreatesPriorityEntryInLiveQueue() throws Exception {
        Biz biz = createBiz();
        String cust = customerToken();
        // book the earliest bookable slot (>= now) of today or tomorrow, check-in opens 60 minutes before start
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        JsonNode slots = get("/api/public/services/" + biz.serviceId() + "/slots?date=" + today, null, 200);
        Optional<JsonNode> soon = java.util.stream.StreamSupport.stream(slots.spliterator(), false)
                .filter(s -> s.get("remaining").asInt() > 0).findFirst();
        if (soon.isEmpty()) return;   // test executed in the last minutes of the UTC day: nothing to book
        JsonNode appt = post("/api/customer/appointments", cust, Map.of("serviceId", biz.serviceId(), "startAt", soon.get().get("startAt").asText()), 201);
        JsonNode checked = post("/api/customer/appointments/" + appt.get("id").asLong() + "/check-in", cust, null, 200);
        assertEquals("CHECKED_IN", checked.get("status").asText());
        assertNotNull(checked.get("queueEntryPublicId"));
        JsonNode view = get("/api/public/entries/" + checked.get("queueEntryPublicId").asText(), null, 200);
        assertEquals("APPOINTMENT", view.get("priority").asText());
        // completing the queue entry completes the appointment
        long entryId = post("/api/queues/call-next", biz.adminToken(), Map.of("counterId", biz.counterId()), 200).get("id").asLong();
        post("/api/queue-entries/" + entryId + "/complete", biz.adminToken(), null, 200);
        assertEquals("COMPLETED", get("/api/customer/appointments", cust, 200).get(0).get("status").asText());
    }
}
