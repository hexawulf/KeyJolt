package com.keyjolt.controller;

import tools.jackson.databind.ObjectMapper;
import com.keyjolt.model.KeyRequest;
import com.keyjolt.model.SshKeyRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
    "app.temp-dir=target/test-keys",
    "app.rate-limit.requests-per-hour=1000",
    "app.rate-limit.burst-capacity=1000"
})
class KeyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void staticAssetsAreCacheable() throws Exception {
        mockMvc.perform(get("/css/style.css"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "max-age=604800, public"));
    }

    @Test
    void apiResponsesAreNotCacheable() throws Exception {
        mockMvc.perform(get("/healthz"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

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
        mockMvc.perform(get("/download/0123456789abcdef0123456789abcdef/nonexistent_file.asc"))
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

    @Test
    void downloadRejectsInvalidToken() throws Exception {
        mockMvc.perform(get("/download/not-a-token/id_ed25519"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Invalid filename."));
    }

    @Test
    void generateSshOnlyEd25519ReturnsTwoFilesAndPublicKey() throws Exception {
        SshKeyRequest request = new SshKeyRequest("ed25519", "zk@laptop", null);

        mockMvc.perform(post("/api/generate/ssh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.files.length()").value(2))
                .andExpect(jsonPath("$.files[0].type").value("ssh_public"))
                .andExpect(jsonPath("$.files[0].filename").value("id_ed25519.pub"))
                .andExpect(jsonPath("$.files[1].type").value("ssh_private"))
                .andExpect(jsonPath("$.files[1].filename").value("id_ed25519"))
                .andExpect(jsonPath("$.publicKey").value(org.hamcrest.Matchers.startsWith("ssh-ed25519 ")))
                .andExpect(jsonPath("$.publicKey").value(org.hamcrest.Matchers.endsWith(" zk@laptop")))
                .andExpect(jsonPath("$.fingerprint").value(org.hamcrest.Matchers.startsWith("SHA256:")));
    }

    @Test
    void generateSshOnlyDefaultsToEd25519WhenKeyTypeOmitted() throws Exception {
        mockMvc.perform(post("/api/generate/ssh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files[1].filename").value("id_ed25519"));
    }

    @Test
    void generateSshOnlyRsaWithPassphrase() throws Exception {
        SshKeyRequest request = new SshKeyRequest("rsa", "", "Secret123!");

        mockMvc.perform(post("/api/generate/ssh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files[1].filename").value("id_rsa"))
                .andExpect(jsonPath("$.publicKey").value(org.hamcrest.Matchers.startsWith("ssh-rsa ")));
    }

    @Test
    void generateSshOnlyRejectsUnknownKeyType() throws Exception {
        SshKeyRequest request = new SshKeyRequest("dsa", null, null);

        mockMvc.perform(post("/api/generate/ssh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void generateSshOnlyRejectsShortPassphrase() throws Exception {
        SshKeyRequest request = new SshKeyRequest("ed25519", null, "short");

        mockMvc.perform(post("/api/generate/ssh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Passphrase must be at least 8 characters if provided."));
    }

    @Test
    void generateSshOnlyRejectsCommentWithNewline() throws Exception {
        SshKeyRequest request = new SshKeyRequest("ed25519", "zk\nssh-rsa AAAA evil", null);

        mockMvc.perform(post("/api/generate/ssh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sshOnlyPrivateKeyDownloadsWithNoStore() throws Exception {
        String body = mockMvc.perform(post("/api/generate/ssh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyType\":\"ed25519\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String url = objectMapper.readTree(body).get("files").get(1).get("downloadUrl").asText();
        org.junit.jupiter.api.Assertions.assertTrue(url.matches("^/download/[a-f0-9]{32}/id_ed25519$"), url);

        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"id_ed25519\""))
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("-----BEGIN OPENSSH PRIVATE KEY-----")));
    }

    @Test
    void validateFieldRejectsBadComment() throws Exception {
        mockMvc.perform(post("/api/validate")
                        .param("field", "comment")
                        .param("value", "bad;comment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
    }

    /** KJ-07: TLS ends at the proxy, so HSTS must be sent on plain-HTTP requests too. */
    @Test
    void hstsHeaderIsSentEvenThoughTheAppSeesPlainHttp() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));
    }

    /** KJ-05: a very long (but syntactically valid) e-mail used to fail late with a 500. */
    @Test
    void generateKeysRejectsOverlongEmailBeforeGeneratingAnything() throws Exception {
        String email = "a".repeat(60) + "@" + "b".repeat(50) + ".example.com";
        KeyRequest request = new KeyRequest("Test User", email, 4096, 0, true, null);

        long started = System.nanoTime();
        mockMvc.perform(post("/api/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Email must not exceed 100 characters")));
        org.junit.jupiter.api.Assertions.assertTrue(
                (System.nanoTime() - started) / 1_000_000 < 1500, "rejected before any RSA work");
    }

    @Test
    void generateKeysAcceptsEmailAtTheLengthLimit() throws Exception {
        String email = "a".repeat(60) + "@" + "b".repeat(27) + ".example.com"; // 100 characters
        org.junit.jupiter.api.Assertions.assertEquals(100, email.length());
        KeyRequest request = new KeyRequest("N".repeat(50), email, 2048, 0, true, null);

        mockMvc.perform(post("/api/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.files.length()").value(4));
    }

    /** KJ-05: \\s let tabs and line breaks into the PGP user ID. */
    @Test
    void generateKeysRejectsControlWhitespaceInName() throws Exception {
        for (String name : new String[] {"Evil\nName", "Tab\tName", "CR\rName"}) {
            KeyRequest request = new KeyRequest(name, "test@example.com", 2048, 0, false, null);
            mockMvc.perform(post("/api/generate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test
    void validateFieldAppliesTheSameNameAndEmailRules() throws Exception {
        mockMvc.perform(post("/api/validate").param("field", "name").param("value", "Tab\tName"))
                .andExpect(jsonPath("$.valid").value(false));
        mockMvc.perform(post("/api/validate").param("field", "name").param("value", "Plain Name 1"))
                .andExpect(jsonPath("$.valid").value(true));
        mockMvc.perform(post("/api/validate").param("field", "email")
                        .param("value", "a".repeat(95) + "@example.com"))
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").value("Email must not exceed 100 characters"));
    }
}
