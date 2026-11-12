package com.keyjolt.controller;

import com.keyjolt.model.KeyRequest;
import com.keyjolt.model.KeyResponse;
import com.keyjolt.model.SshKeyRequest;
import com.keyjolt.service.PgpKeyService;
import com.keyjolt.service.SshKeyService;
import com.keyjolt.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;

/**
 * Main controller for handling key generation requests and file downloads
 */
@Controller
public class KeyController {

    private static final Logger logger = LoggerFactory.getLogger(KeyController.class);

    @Autowired
    private PgpKeyService pgpKeyService;

    @Autowired
    private SshKeyService sshKeyService;

    @Autowired
    private FileUtils fileUtils;

    @Value("${app.rate-limit.requests-per-hour:10}")
    private int requestsPerHour;

    @Value("${app.rate-limit.burst-capacity:5}")
    private int burstCapacity;

    @Value("${app.trusted-proxy:false}")
    private boolean trustedProxy;

    // Rate limiting buckets per IP
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * Show the main key generation form
     */
    @GetMapping("/")
    public String index(Model model) {
        logger.debug("Serving index page");
        model.addAttribute("keyRequest", new KeyRequest());
        model.addAttribute("encryptionStrengths", new int[]{2048, 3072, 4096});
        return "index";
    }

    /**
     * Generate PGP and optionally SSH key pairs
     */
    @PostMapping("/api/generate")
    @ResponseBody
    public ResponseEntity<KeyResponse> generateKeys(
            @Valid @RequestBody KeyRequest request,
            BindingResult bindingResult,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIpAddress(httpRequest);
        logger.info("Key generation request from {}", clientIp);

        if (!getBucket(clientIp).tryConsume(1)) {
            return rateLimited();
        }

        if (bindingResult.hasErrors()) {
            return validationFailed(bindingResult);
        }

        if (!request.hasValidEncryptionStrength()) {
            return ResponseEntity.badRequest()
                .body(KeyResponse.error("Encryption strength must be 2048, 3072, or 4096."));
        }

        if (!request.hasValidPassword()) {
            return ResponseEntity.badRequest()
                .body(KeyResponse.error("Password must be at least 8 characters if provided."));
        }

        try {
            String requestDir = fileUtils.newRequestDir();
            List<KeyResponse.FileInfo> files = new ArrayList<>();

            // The SSH RSA key is independent of the PGP keys: start it first so it
            // is generated on another core while the PGP keys are being built.
            CompletableFuture<SshKeyService.SshKeyPair> sshFuture = null;
            if (request.isGenerateSshKey()) {
                sshFuture = CompletableFuture.supplyAsync(() -> {
                    try {
                        return sshKeyService.generateKeyPair(request, requestDir);
                    } catch (IOException | GeneralSecurityException e) {
                        throw new CompletionException(e);
                    }
                });
            }

            PgpKeyService.PgpKeyPair pgpKeys = pgpKeyService.generateKeyPair(request, requestDir);
            files.add(fileInfo(requestDir, pgpKeys.getPublicKeyFile(), "pgp_public"));
            files.add(fileInfo(requestDir, pgpKeys.getPrivateKeyFile(), "pgp_private"));

            if (sshFuture != null) {
                SshKeyService.SshKeyPair sshKeys = sshFuture.join();
                files.add(fileInfo(requestDir, sshKeys.getPublicKeyFile(), "ssh_public"));
                files.add(fileInfo(requestDir, sshKeys.getPrivateKeyFile(), "ssh_private"));
            }

            logger.info("Keys generated successfully (strength={})", request.getEncryptionStrength());

            return ResponseEntity.ok(KeyResponse.success(
                "Keys generated successfully! Download them below.",
                pgpKeys.getKeyId(),
                files
            ));

        } catch (Exception e) {
            logger.error("Failed to generate keys", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(KeyResponse.error("Key generation failed. Please try again."));
        }
    }

    /**
     * Generate a standalone SSH key pair (no PGP)
     */
    @PostMapping("/api/generate/ssh")
    @ResponseBody
    public ResponseEntity<KeyResponse> generateSshKeys(
            @Valid @RequestBody SshKeyRequest request,
            BindingResult bindingResult,
            HttpServletRequest httpRequest) {

        String clientIp = getClientIpAddress(httpRequest);
        logger.info("SSH key generation request from {}", clientIp);

        if (!getBucket(clientIp).tryConsume(1)) {
            return rateLimited();
        }

        if (bindingResult.hasErrors()) {
            return validationFailed(bindingResult);
        }

        if (!request.hasValidPassphrase()) {
            return ResponseEntity.badRequest()
                .body(KeyResponse.error("Passphrase must be at least 8 characters if provided."));
        }

        try {
            String requestDir = fileUtils.newRequestDir();
            SshKeyService.SshKeyPair sshKeys = sshKeyService.generateKeyPair(request, requestDir);

            List<KeyResponse.FileInfo> files = List.of(
                fileInfo(requestDir, sshKeys.getPublicKeyFile(), "ssh_public"),
                fileInfo(requestDir, sshKeys.getPrivateKeyFile(), "ssh_private")
            );

            KeyResponse response = KeyResponse.success(
                "SSH key pair generated! Download it below.", null, files);
            response.setFingerprint(sshKeys.getFingerprint());
            response.setPublicKey(sshKeys.getPublicKeyContent());

            logger.info("SSH keys generated successfully (type={}, passphrase={})",
                request.getKeyType(), request.hasPassphrase());
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            logger.error("Failed to generate SSH keys", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(KeyResponse.error("Key generation failed. Please try again."));
        }
    }

    /**
     * Download generated key files. The request token is a random 128-bit
     * directory name, so URLs cannot be guessed from a name/email/key ID.
     */
    @GetMapping("/download/{token}/{filename}")
    public ResponseEntity<?> downloadFile(@PathVariable String token, @PathVariable String filename) {
        try {
            Path filePath = fileUtils.resolveDownloadPath(token, filename);
            if (filePath == null) {
                logger.warn("Rejected download request for invalid path");
                return jsonError(HttpStatus.BAD_REQUEST, "Invalid filename.");
            }

            if (!Files.exists(filePath)) {
                return jsonError(HttpStatus.NOT_FOUND, "File not found.");
            }

            Resource fileResource = new UrlResource(filePath.toUri());

            if (!fileResource.exists() || !fileResource.isReadable()) {
                return jsonError(HttpStatus.INTERNAL_SERVER_ERROR, "File is not readable.");
            }

            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .body(fileResource);

        } catch (Exception e) {
            logger.error("Download failed for file: {}", filename, e);
            return jsonError(HttpStatus.INTERNAL_SERVER_ERROR, "Download failed.");
        }
    }

    /**
     * Validate individual form fields via AJAX
     */
    @PostMapping("/api/validate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> validateField(
            @RequestParam String field,
            @RequestParam String value) {

        Map<String, Object> response = new HashMap<>();
        boolean isValid = true;
        String message = "";

        switch (field) {
            case "name":
                if (value == null || value.trim().isEmpty()) {
                    isValid = false;
                    message = "Name is required";
                } else if (value.length() > 50) {
                    isValid = false;
                    message = "Name must not exceed 50 characters";
                } else if (!value.matches("^[a-zA-Z0-9\\s]+$")) {
                    isValid = false;
                    message = "Name can only contain letters, numbers, and spaces";
                }
                break;

            case "email":
                if (value == null || value.trim().isEmpty()) {
                    isValid = false;
                    message = "Email is required";
                } else if (!value.matches("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$")) {
                    isValid = false;
                    message = "Please enter a valid email address";
                }
                break;

            case "keyExpiry":
                try {
                    int expiry = Integer.parseInt(value);
                    if (expiry < 0) {
                        isValid = false;
                        message = "Key expiry must be 0 or positive";
                    } else if (expiry > 3650) {
                        isValid = false;
                        message = "Maximum key expiry is 3650 days (10 years)";
                    }
                } catch (NumberFormatException e) {
                    isValid = false;
                    message = "Key expiry must be a valid number";
                }
                break;

            case "password":
                if (value != null && !value.isEmpty() && value.length() < 8) {
                    isValid = false;
                    message = "Password must be at least 8 characters";
                }
                break;

            case "comment":
                if (value != null && value.length() > 100) {
                    isValid = false;
                    message = "Comment must not exceed 100 characters";
                } else if (value != null && !value.matches("^[A-Za-z0-9@._+\\- ]*$")) {
                    isValid = false;
                    message = "Comment can only contain letters, numbers, spaces and @ . _ + -";
                }
                break;

            default:
                break;
        }

        response.put("valid", isValid);
        response.put("message", message);

        return ResponseEntity.ok(response);
    }

    private KeyResponse.FileInfo fileInfo(String requestDir, File file, String type) {
        return new KeyResponse.FileInfo(
            file.getName(),
            "/download/" + requestDir + "/" + file.getName(),
            type,
            fileUtils.getFileSize(file)
        );
    }

    private static ResponseEntity<KeyResponse> rateLimited() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(KeyResponse.error("Rate limit exceeded. Please try again later."));
    }

    private static ResponseEntity<KeyResponse> validationFailed(BindingResult bindingResult) {
        StringBuilder errors = new StringBuilder();
        bindingResult.getFieldErrors().forEach(error ->
            errors.append(error.getDefaultMessage()).append("; "));
        return ResponseEntity.badRequest()
            .body(KeyResponse.error("Validation failed: " + errors));
    }

    private static ResponseEntity<Map<String, String>> jsonError(HttpStatus status, String message) {
        return ResponseEntity.status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("error", message));
    }

    /**
     * Get rate limiting bucket for IP address
     */
    private Bucket getBucket(String ip) {
        return buckets.computeIfAbsent(ip, k -> {
            Bandwidth limit = Bandwidth.classic(requestsPerHour, Refill.intervally(requestsPerHour, Duration.ofHours(1)));
            Bandwidth burst = Bandwidth.classic(burstCapacity, Refill.greedy(burstCapacity, Duration.ofMinutes(1)));
            return Bucket4j.builder()
                .addLimit(limit)
                .addLimit(burst)
                .build();
        });
    }

    @Scheduled(fixedRate = 3600000)
    public void evictStaleBuckets() {
        int before = buckets.size();
        Iterator<Map.Entry<String, Bucket>> it = buckets.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Bucket> entry = it.next();
            if (entry.getValue().getAvailableTokens() >= requestsPerHour) {
                it.remove();
            }
        }
        int removed = before - buckets.size();
        if (removed > 0) {
            logger.debug("Evicted {} stale rate-limit buckets", removed);
        }
    }

    /**
     * Get client IP address from request.
     * Only trusts proxy headers when app.trusted-proxy=true (i.e. behind a
     * known reverse proxy like nginx). Otherwise falls back to remoteAddr
     * so rate limiting cannot be trivially bypassed via spoofed headers.
     *
     * X-Real-IP is preferred because the proxy overwrites it. X-Forwarded-For is
     * appended to (nginx $proxy_add_x_forwarded_for), so its FIRST entry is
     * client-controlled; only the LAST entry, added by our proxy, is trusted.
     */
    String getClientIpAddress(HttpServletRequest request) {
        if (trustedProxy) {
            String xRealIp = request.getHeader("X-Real-IP");
            if (xRealIp != null && !xRealIp.isBlank()) {
                return xRealIp.trim();
            }

            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (xForwardedFor != null && !xForwardedFor.isBlank()) {
                String[] hops = xForwardedFor.split(",");
                return hops[hops.length - 1].trim();
            }
        }

        return request.getRemoteAddr();
    }
}
