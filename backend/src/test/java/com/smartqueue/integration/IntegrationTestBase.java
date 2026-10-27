package com.smartqueue.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class IntegrationTestBase {
    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper om;

    /** A fully configured test tenant that is open 24/7 in UTC. */
    record Biz(String adminToken, long businessId, long serviceId, String queuePublicId, long queueId, long counterId) {}

    protected JsonNode call(MockHttpServletRequestBuilder b, String token, Object body, int expected) throws Exception {
        MvcResult r = callRaw(b, token, body);
        assertEquals(expected, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        String s = r.getResponse().getContentAsString();
        return s.isBlank() ? null : om.readTree(s);
    }

    protected MvcResult callRaw(MockHttpServletRequestBuilder b, String token, Object body) throws Exception {
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (body != null) b.contentType(MediaType.APPLICATION_JSON).content(om.writeValueAsString(body));
        return mvc.perform(b).andReturn();
    }

    protected JsonNode post(String url, String token, Object body, int expected) throws Exception {
        return call(MockMvcRequestBuilders.post(url), token, body, expected);
    }

    protected JsonNode get(String url, String token, int expected) throws Exception {
        return call(MockMvcRequestBuilders.get(url), token, null, expected);
    }

    protected JsonNode put(String url, String token, Object body, int expected) throws Exception {
        return call(MockMvcRequestBuilders.put(url), token, body, expected);
    }

    protected JsonNode patch(String url, String token, Object body, int expected) throws Exception {
        return call(MockMvcRequestBuilders.patch(url), token, body, expected);
    }

    protected static String uid() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    protected JsonNode registerBusiness(String name, String email) throws Exception {
        return post("/api/auth/register/business", null, Map.of("businessName", name, "timezone", "UTC", "adminFullName", "Admin " + name,
                "email", email, "password", "Passw0rdTest"), 201);
    }

    protected JsonNode registerCustomer(String email) throws Exception {
        return post("/api/auth/register/customer", null, Map.of("email", email, "password", "Passw0rdTest", "fullName", "Cust " + email), 201);
    }

    protected String customerToken() throws Exception {
        return registerCustomer("c" + uid() + "@test.io").get("token").asText();
    }

    protected Biz createBiz() throws Exception {
        return createBiz(1);
    }

    protected Biz createBiz(int counters) throws Exception {
        String s = uid();
        JsonNode reg = registerBusiness("Biz " + s, "admin" + s + "@test.io");
        String token = reg.get("token").asText();
        long businessId = reg.get("user").get("businessId").asLong();
        List<Map<String, Object>> days = new ArrayList<>();
        for (String d : List.of("MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")) {
            days.add(Map.of("dayOfWeek", d, "openTime", "00:00:00", "closeTime", "23:59:00", "closed", false));
        }
        put("/api/business/hours", token, Map.of("days", days), 200);
        JsonNode svc = post("/api/services", token, Map.of("name", "General", "estimatedDurationMinutes", 5, "appointmentsEnabled", true,
                "slotIntervalMinutes", 5, "queueEnabled", true), 201);
        long serviceId = svc.get("id").asLong();
        long counterId = 0;
        for (int i = 1; i <= counters; i++) {
            JsonNode c = post("/api/counters", token, Map.of("name", "C" + i, "serviceIds", List.of(serviceId)), 201);
            patch("/api/counters/" + c.get("id").asLong() + "/status", token, Map.of("status", "OPEN"), 200);
            if (i == 1) counterId = c.get("id").asLong();
        }
        return new Biz(token, businessId, serviceId, svc.get("queuePublicId").asText(), svc.get("queueId").asLong(), counterId);
    }

    protected JsonNode guestJoin(Biz b, String name) throws Exception {
        return post("/api/public/queues/" + b.queuePublicId() + "/join", null, Map.of("guestName", name), 201);
    }
}
