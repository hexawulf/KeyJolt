package com.keyjolt.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.Semaphore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Production-like limits (10 per hour, burst 5) with a single generation slot.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "app.temp-dir=target/test-keys",
    "app.rate-limit.requests-per-hour=10",
    "app.rate-limit.burst-capacity=5",
    "app.generation.max-concurrent=1",
    "app.generation.queue-wait-ms=50"
})
class KeyControllerLimitsTest {

    private static final String PGP_BODY =
        "{\"name\":\"Test User\",\"email\":\"test@example.com\",\"encryptionStrength\":2048,\"keyExpiry\":0}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private KeyController controller;

    /**
     * KJ-04: the old check compared against the hourly limit (10), which a bucket
     * capped at the burst size (5) never reaches, so nothing was ever evicted.
     */
    @Test
    void idleBucketsAreEvictedAndUsedOnesAreKept() {
        controller.evictStaleBuckets();
        int base = controller.bucketCount();

        controller.getBucket("203.0.113.1");                       // untouched: full
        assertTrue(controller.getBucket("203.0.113.2").tryConsume(1)); // used: not full
        assertEquals(5, controller.getBucket("203.0.113.1").getAvailableTokens());
        assertEquals(base + 2, controller.bucketCount());

        controller.evictStaleBuckets();

        assertEquals(base + 1, controller.bucketCount());
        assertEquals(4, controller.getBucket("203.0.113.2").getAvailableTokens(), "used bucket survived");
    }

    /** KJ-06: when every generation slot is taken, heavy requests get 503 instead of piling up. */
    @Test
    void heavyRequestsGet503WhileAllSlotsAreBusyAndKeepTheirRateLimitToken() throws Exception {
        Semaphore slots = (Semaphore) ReflectionTestUtils.getField(controller, "generationSlots");
        assertEquals(1, slots.availablePermits());
        slots.acquire();
        try {
            long before = controller.getBucket("127.0.0.1").getAvailableTokens();

            mockMvc.perform(post("/api/generate").contentType(MediaType.APPLICATION_JSON).content(PGP_BODY))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().string("Retry-After", "10"))
                    .andExpect(jsonPath("$.success").value(false));
            mockMvc.perform(post("/api/generate/ssh").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"keyType\":\"rsa\"}"))
                    .andExpect(status().isServiceUnavailable());

            assertEquals(before, controller.getBucket("127.0.0.1").getAvailableTokens(),
                    "a 503 does not cost the client a rate-limit token");

            // Ed25519 is cheap and does not need a slot
            mockMvc.perform(post("/api/generate/ssh").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"keyType\":\"ed25519\"}"))
                    .andExpect(status().isOk());
        } finally {
            slots.release();
        }

        mockMvc.perform(post("/api/generate").contentType(MediaType.APPLICATION_JSON).content(PGP_BODY))
                .andExpect(status().isOk());
        assertEquals(1, slots.availablePermits(), "slot returned after a successful generation");
    }

    @Test
    void slotIsReturnedAfterAValidationError() throws Exception {
        Semaphore slots = (Semaphore) ReflectionTestUtils.getField(controller, "generationSlots");
        mockMvc.perform(post("/api/generate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"x\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(1, slots.availablePermits());
    }
}
