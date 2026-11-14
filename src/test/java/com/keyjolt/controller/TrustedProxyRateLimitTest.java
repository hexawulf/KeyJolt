package com.keyjolt.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Behind nginx (app.trusted-proxy=true) each client gets its own bucket, keyed on
 * X-Real-IP, and a spoofed X-Forwarded-For cannot be used to dodge the limit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "app.temp-dir=target/test-keys",
    "app.trusted-proxy=true",
    "app.rate-limit.requests-per-hour=2",
    "app.rate-limit.burst-capacity=2"
})
class TrustedProxyRateLimitTest {

    @Autowired
    private MockMvc mockMvc;

    private static MockHttpServletRequestBuilder sshRequest(String realIp, String forwardedFor) {
        MockHttpServletRequestBuilder builder = post("/api/generate/ssh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"keyType\":\"ed25519\"}");
        if (realIp != null) {
            builder.header("X-Real-IP", realIp);
        }
        if (forwardedFor != null) {
            builder.header("X-Forwarded-For", forwardedFor);
        }
        return builder;
    }

    @Test
    void spoofedForwardedForDoesNotBypassLimitAndClientsAreSeparate() throws Exception {
        // Client A rotates a fake first X-Forwarded-For hop on every request
        mockMvc.perform(sshRequest("198.51.100.10", "1.1.1.1, 198.51.100.10")).andExpect(status().isOk());
        mockMvc.perform(sshRequest("198.51.100.10", "2.2.2.2, 198.51.100.10")).andExpect(status().isOk());
        mockMvc.perform(sshRequest("198.51.100.10", "3.3.3.3, 198.51.100.10")).andExpect(status().isTooManyRequests());

        // Client B has its own bucket
        mockMvc.perform(sshRequest("203.0.113.20", null)).andExpect(status().isOk());

        // Without X-Real-IP, only the proxy-appended (last) X-Forwarded-For hop counts
        mockMvc.perform(sshRequest(null, "9.9.9.9, 192.0.2.30")).andExpect(status().isOk());
        mockMvc.perform(sshRequest(null, "8.8.8.8, 192.0.2.30")).andExpect(status().isOk());
        mockMvc.perform(sshRequest(null, "7.7.7.7, 192.0.2.30")).andExpect(status().isTooManyRequests());
    }
}
