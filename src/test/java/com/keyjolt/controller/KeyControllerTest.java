package com.keyjolt.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.keyjolt.model.KeyRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "app.rate-limit.requests-per-hour=1000",
    "app.rate-limit.burst-capacity=1000"
})
class KeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void indexPageReturns200() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    @Test
    void generateKeysReturnsSuccessForValidRequest() throws Exception {
        KeyRequest request = new KeyRequest("Test User", "test@example.com", 2048, 30, false, null);

        mockMvc.perform(post("/api/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.files").isArray())
                .andExpect(jsonPath("$.files.length()").value(2))
                .andExpect(jsonPath("$.files[0].downloadUrl").isString())
                .andExpect(jsonPath("$.keyId").isString());
    }

    @Test
    void generateKeysReturnsSuccessWithSsh() throws Exception {
        KeyRequest request = new KeyRequest("Test User", "test@example.com", 2048, 0, true, null);

        mockMvc.perform(post("/api/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.files.length()").value(4));
    }

    @Test
    void generateKeysRejectsMissingName() throws Exception {
        KeyRequest request = new KeyRequest(null, "test@example.com", 4096, 30, false, null);

        mockMvc.perform(post("/api/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    void generateKeysRejectsInvalidEncryptionStrength() throws Exception {
        KeyRequest request = new KeyRequest("Test User", "test@example.com", 1024, 30, false, null);

        mockMvc.perform(post("/api/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Encryption strength must be 2048, 3072, or 4096."));
    }

    @Test
    void generateKeysRejectsWeakPassword() throws Exception {
        KeyRequest request = new KeyRequest("Test User", "test@example.com", 2048, 30, false, "short");

        mockMvc.perform(post("/api/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Password must be at least 8 characters if provided."));
    }

    @Test
    void generateKeysAcceptsStrongPassword() throws Exception {
        KeyRequest request = new KeyRequest("Test User", "test@example.com", 2048, 30, false, "StrongP@ss1");

        mockMvc.perform(post("/api/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void validateFieldReturnsTrueForValidName() throws Exception {
        mockMvc.perform(post("/api/validate")
                        .param("field", "name")
                        .param("value", "Alice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void validateFieldReturnsFalseForEmptyName() throws Exception {
        mockMvc.perform(post("/api/validate")
                        .param("field", "name")
                        .param("value", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").value("Name is required"));
    }

    @Test
    void validateFieldRejectsShortPassword() throws Exception {
        mockMvc.perform(post("/api/validate")
                        .param("field", "password")
                        .param("value", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").value("Password must be at least 8 characters"));
    }

    @Test
    void validateFieldAcceptsEmptyPassword() throws Exception {
        mockMvc.perform(post("/api/validate")
                        .param("field", "password")
                        .param("value", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void downloadRejects404ForMissingFile() throws Exception {
        mockMvc.perform(get("/download/nonexistent_file.asc"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("File not found."));
    }

    @Test
    void downloadRejectsPathTraversal() throws Exception {
        mockMvc.perform(get("/download/..%2F..%2Fetc%2Fpasswd"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownPathReturnsDenied() throws Exception {
        mockMvc.perform(get("/admin/secret"))
                .andExpect(status().isForbidden());
    }
}
