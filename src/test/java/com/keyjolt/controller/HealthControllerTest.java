package com.keyjolt.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Health endpoint, security headers and error responses: what the reverse
 * proxy, uptime monitors and browsers rely on.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "app.temp-dir=target/test-keys-health",
    "app.rate-limit.requests-per-hour=1000",
    "app.rate-limit.burst-capacity=1000"
})
class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @org.springframework.beans.factory.annotation.Value("${server.address}")
    private String serverAddress;

    @Test
    void defaultBindAddressIsLoopback() {
        org.junit.jupiter.api.Assertions.assertEquals("127.0.0.1", serverAddress);
    }

    @Test
    void healthzIsPublicJsonAndUncached() throws Exception {
        mockMvc.perform(get("/healthz"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value("ok"))
            .andExpect(jsonPath("$.service").value("keyjolt"))
            .andExpect(header().string("Cache-Control", containsString("no-store")))
            // no session is ever created for anonymous probes
            .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void healthzRejectsWrites() throws Exception {
        MvcResult result = mockMvc.perform(post("/healthz"))
            .andExpect(status().is4xxClientError())
            .andReturn();
        assertNoStackTrace(result);
    }

    @Test
    void securityHeadersAreSentOnEveryPage() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
            .andExpect(header().string("Content-Security-Policy", containsString("object-src 'none'")))
            .andExpect(header().string("Content-Security-Policy",
                    containsString("script-src 'self';")))
            .andExpect(header().string("Content-Security-Policy",
                    org.hamcrest.Matchers.not(containsString("cdn.jsdelivr.net"))))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().string("Referrer-Policy", "no-referrer"))
            // TLS ends at nginx, so HSTS must not depend on the request being secure
            .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"));
    }

    @Test
    void hstsIsSentBehindTheTlsProxy() throws Exception {
        mockMvc.perform(get("/healthz").header("X-Forwarded-Proto", "https"))
            .andExpect(status().isOk())
            .andExpect(header().string("Strict-Transport-Security", containsString("max-age=31536000")))
            .andExpect(header().string("Strict-Transport-Security", containsString("includeSubDomains")));
    }

    @Test
    void aboutModalVersionIsTheBuildVersion() throws Exception {
        // app.version is filled in from pom.xml at build time; an unfiltered
        // "@project.version@" would show up in the About modal.
        mockMvc.perform(get("/"))
            .andExpect(model().attribute("appVersion",
                    org.hamcrest.Matchers.matchesPattern("^\\d+\\.\\d+\\.\\d+$")))
            .andExpect(content().string(containsString("Erling Wulf Weinreich (0xWulf) · ")))
            .andExpect(content().string(containsString("href=\"mailto:dev@0xwulf.dev\"")));
    }

    @Test
    void cspDoesNotAllowInlineScripts() throws Exception {
        String csp = mockMvc.perform(get("/"))
            .andReturn().getResponse().getHeader("Content-Security-Policy");
        assertTrue(csp != null && csp.contains("script-src"), String.valueOf(csp));
        String scriptSrc = csp.substring(csp.indexOf("script-src"));
        scriptSrc = scriptSrc.contains(";") ? scriptSrc.substring(0, scriptSrc.indexOf(';')) : scriptSrc;
        assertFalse(scriptSrc.contains("'unsafe-inline'"), scriptSrc);
        assertFalse(scriptSrc.contains("'unsafe-eval'"), scriptSrc);
    }

    @Test
    void indexPageHasNoInlineEventHandlers() throws Exception {
        // The CSP blocks inline handlers, so an onclick="" would be a dead button.
        String html = mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertFalse(html.matches("(?s).*\\son[a-z]+\\s*=\\s*\".*"), "inline event handler found in index.html");
    }

    @Test
    void sitemapXmlIsPublic() throws Exception {
        mockMvc.perform(get("/sitemap.xml"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
            .andExpect(content().string(containsString("http://www.sitemaps.org/schemas/sitemap/0.9")))
            .andExpect(content().string(containsString("<loc>https://keyjolt.dev/</loc>")))
            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("https://www.keyjolt.dev"))));
    }

    @Test
    void robotsTxtAdvertisesSitemap() throws Exception {
        mockMvc.perform(get("/robots.txt"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
            .andExpect(content().string(containsString("Sitemap: https://keyjolt.dev/sitemap.xml")))
            .andExpect(content().string(containsString("Disallow: /api/")))
            .andExpect(content().string(containsString("Disallow: /download/")));
    }

    @Test
    void homepageHasCanonicalAndDescription() throws Exception {
        String html = mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertTrue(html.contains("<link rel=\"canonical\" href=\"https://keyjolt.dev/\">"), html);
        assertTrue(html.contains("<meta name=\"description\" content=\"Create cryptographically secure PGP and SSH key pairs instantly. Private keys are generated on the server and automatically deleted after 5 minutes.\">"), html);
        assertFalse(html.contains("https://www.keyjolt.dev"), html);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"/does-not-exist", "/api/unknown", "/actuator/env", "/wp-admin/install.php",
            "/wp-login.php", "/.env", "/.git/config", "/generate", "/admin/", "/admin/keys"})
    void unknownAndProbedRoutesAreDeniedWithoutDetails(String path) throws Exception {
        MvcResult result = mockMvc.perform(get(path))
            .andExpect(status().isForbidden())
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andReturn();
        assertNoStackTrace(result);
    }

    @Test
    void malformedJsonIsAClean400() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/generate/ssh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not json"))
            .andExpect(status().isBadRequest())
            .andReturn();
        assertNoStackTrace(result);
    }

    @Test
    void wrongContentTypeIsAClean415() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/generate/ssh")
                .contentType(MediaType.TEXT_PLAIN)
                .content("keyType=ed25519"))
            .andExpect(status().isUnsupportedMediaType())
            .andReturn();
        assertNoStackTrace(result);
    }

    private static void assertNoStackTrace(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        assertFalse(body.contains("Exception"), body);
        assertFalse(body.contains("\tat "), body);
        assertFalse(body.contains("com.keyjolt."), body);
        assertFalse(body.contains("org.springframework."), body);
    }
}
