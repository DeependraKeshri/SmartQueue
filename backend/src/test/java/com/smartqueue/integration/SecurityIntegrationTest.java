package com.smartqueue.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.smartqueue.entity.AppUser;
import com.smartqueue.repository.AppUserRepository;
import com.smartqueue.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SecurityIntegrationTest extends IntegrationTestBase {
    @Autowired JwtService jwt;
    @Autowired AppUserRepository users;

    @Test
    void registerLoginAndNeverExposePassword() throws Exception {
        String email = "u" + uid() + "@test.io";
        JsonNode reg = registerCustomer(email);
        assertFalse(reg.toString().toLowerCase().contains("password"));
        JsonNode login = post("/api/auth/login", null, Map.of("email", email, "password", "Passw0rdTest"), 200);
        assertEquals("CUSTOMER", login.get("user").get("role").asText());
        assertNotNull(login.get("token"));
        JsonNode me = get("/api/users/me", login.get("token").asText(), 200);
        assertEquals(email, me.get("email").asText());
    }

    @Test
    void wrongPasswordAndUnknownEmailLookTheSame() throws Exception {
        String email = "u" + uid() + "@test.io";
        registerCustomer(email);
        JsonNode a = post("/api/auth/login", null, Map.of("email", email, "password", "WrongPass123"), 401);
        JsonNode b = post("/api/auth/login", null, Map.of("email", "nobody" + uid() + "@test.io", "password", "WrongPass123"), 401);
        assertEquals(a.get("message").asText(), b.get("message").asText());
    }

    @Test
    void duplicateEmailAndWeakPasswordAreRejected() throws Exception {
        String email = "u" + uid() + "@test.io";
        registerCustomer(email);
        post("/api/auth/register/customer", null, Map.of("email", email, "password", "Passw0rdTest", "fullName", "X"), 409);
        post("/api/auth/register/customer", null, Map.of("email", "w" + uid() + "@test.io", "password", "short", "fullName", "X"), 400);
    }

    @Test
    void accountLocksAfterRepeatedFailures() throws Exception {
        String email = "u" + uid() + "@test.io";
        registerCustomer(email);
        for (int i = 0; i < 5; i++) {
            post("/api/auth/login", null, Map.of("email", email, "password", "WrongPass123"), 401);
        }
        JsonNode locked = post("/api/auth/login", null, Map.of("email", email, "password", "Passw0rdTest"), 401);
        assertTrue(locked.get("message").asText().toLowerCase().contains("locked"));
    }

    @Test
    void protectedEndpointsRequireValidToken() throws Exception {
        get("/api/queues", null, 401);
        get("/api/queues", "garbage.token.value", 401);
        AppUser u = users.findByEmailIgnoreCase("nonexistent@x.io").orElse(null);
        assertNull(u);
        String cust = customerToken();
        AppUser stored = users.findAll().stream().filter(x -> x.getRole().name().equals("CUSTOMER")).findFirst().orElseThrow();
        String expired = jwt.generate(stored, Duration.ofSeconds(-30));
        get("/api/users/me", expired, 401);
        get("/api/users/me", cust, 200);
    }

    @Test
    void roleBasedAccessControl() throws Exception {
        Biz biz = createBiz();
        String cust = customerToken();
        get("/api/queues", cust, 403);
        get("/api/analytics/daily", cust, 403);
        get("/api/audit-logs", cust, 403);
        get("/api/platform/businesses", biz.adminToken(), 403);
        get("/api/queues", biz.adminToken(), 200);
        post("/api/queues/" + biz.queueId() + "/walk-ins", cust, Map.of("guestName", "x"), 403);
    }

    @Test
    void tenantIsolation() throws Exception {
        Biz a = createBiz();
        Biz b = createBiz();
        JsonNode entry = guestJoin(a, "Alice Anderson").get("entry");
        long entryId = entry.get("id").asLong();
        // business B cannot see or act on business A's data
        get("/api/queues/" + a.queueId(), b.adminToken(), 404);
        post("/api/queue-entries/" + entryId + "/cancel", b.adminToken(), null, 404);
        post("/api/queue-entries/" + entryId + "/complete", b.adminToken(), null, 404);
        post("/api/queues/call-next", b.adminToken(), Map.of("counterId", a.counterId()), 404);
        put("/api/services/" + a.serviceId(), b.adminToken(), Map.of("name", "Hacked"), 404);
        // and A's lists never contain B's records
        JsonNode servicesOfB = get("/api/services", b.adminToken(), 200);
        for (JsonNode s : servicesOfB) assertNotEquals(a.serviceId(), s.get("id").asLong());
        // staff creation is confined to the actor's own business
        JsonNode staff = get("/api/staff", a.adminToken(), 200);
        assertEquals(1, staff.size());
    }

    @Test
    void staffCannotOperateAnotherStaffsCounterOrManageBusiness() throws Exception {
        Biz biz = createBiz(2);
        JsonNode counters = get("/api/counters", biz.adminToken(), 200);
        long c1 = counters.get(0).get("id").asLong();
        long c2 = counters.get(1).get("id").asLong();
        String email = "staff" + uid() + "@test.io";
        JsonNode staff = post("/api/staff", biz.adminToken(), Map.of("email", email, "password", "Passw0rdTest", "fullName", "Sam Staff",
                "role", "STAFF"), 201);
        put("/api/counters/" + c1, biz.adminToken(), Map.of("name", "C1", "assignedStaffId", staff.get("id").asLong(),
                "serviceIds", java.util.List.of(biz.serviceId())), 200);
        String st = post("/api/auth/login", null, Map.of("email", email, "password", "Passw0rdTest"), 200).get("token").asText();
        guestJoin(biz, "Guest One");
        post("/api/queues/call-next", st, Map.of("counterId", c2), 403);          // not their counter
        post("/api/queues/call-next", st, Map.of("counterId", c1), 200);          // own counter
        post("/api/services", st, Map.of("name", "Nope"), 403);                    // staff cannot manage services
        patch("/api/business", st, Map.of("name", "Hijack"), 403);
    }
}
