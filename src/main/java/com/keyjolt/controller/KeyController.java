package com.keyjolt.controller;

import com.keyjolt.model.KeyRequest;
import com.keyjolt.model.KeyResponse;
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
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
    
    // Rate limiting buckets per IP
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    
    /**
     * Show the main key generation form
     */
    @GetMapping("/")
    public String index(Model model) {
        logger.info("Serving index page");
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

        logger.info("Received key generation request from {}", getClientIpAddress(httpRequest));

        // Check rate limiting
        String clientIp = getClientIpAddress(httpRequest);
        Bucket bucket = getBucket(clientIp);
        
        if (!bucket.tryConsume(1)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(KeyResponse.error("Rate limit exceeded. Please try again later."));
        }
        
        // Validate request
        if (bindingResult.hasErrors()) {
            StringBuilder errors = new StringBuilder();
            bindingResult.getFieldErrors().forEach(error -> 
                errors.append(error.getDefaultMessage()).append("; "));
            return ResponseEntity.badRequest()
                .body(KeyResponse.error("Validation failed: " + errors.toString()));
        }
        
        try {
            List<KeyResponse.FileInfo> files = new ArrayList<>();
            
            // Generate PGP key pair
            PgpKeyService.PgpKeyPair pgpKeys = pgpKeyService.generateKeyPair(request);
            
            // Add PGP files to response
            files.add(new KeyResponse.FileInfo(
                pgpKeys.getPublicKeyFile().getName(),
                "/download/" + pgpKeys.getPublicKeyFile().getName(),
                "pgp_public",
                fileUtils.getFileSize(pgpKeys.getPublicKeyFile())
            ));
            
            files.add(new KeyResponse.FileInfo(
                pgpKeys.getPrivateKeyFile().getName(),
                "/download/" + pgpKeys.getPrivateKeyFile().getName(),
                "pgp_private",
                fileUtils.getFileSize(pgpKeys.getPrivateKeyFile())
            ));
            
            // Generate SSH key pair if requested
            if (request.isGenerateSshKey()) {
                SshKeyService.SshKeyPair sshKeys = sshKeyService.generateKeyPair(request);
                
                files.add(new KeyResponse.FileInfo(
                    sshKeys.getPublicKeyFile().getName(),
                    "/download/" + sshKeys.getPublicKeyFile().getName(),
                    "ssh_public",
                    fileUtils.getFileSize(sshKeys.getPublicKeyFile())
                ));
                
                files.add(new KeyResponse.FileInfo(
                    sshKeys.getPrivateKeyFile().getName(),
                    "/download/" + sshKeys.getPrivateKeyFile().getName(),
                    "ssh_private",
                    fileUtils.getFileSize(sshKeys.getPrivateKeyFile())
                ));
            }

            logger.info("Generated keys for {}", request.getEmail());

            return ResponseEntity.ok(KeyResponse.success(
                "Keys generated successfully! Download them below.",
                pgpKeys.getKeyId(),
                files
            ));
            
        } catch (Exception e) {
            logger.error("Failed to generate keys: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(KeyResponse.error("Failed to generate keys: " + e.getMessage()));
        }
    }
    
    /**
     * Download generated key files
     */
    @GetMapping("/download/{filename}")
    public ResponseEntity<Resource> downloadFile(
            @PathVariable String filename,
            HttpServletResponse response) { // HttpServletResponse might not be needed anymore

        try {
            // Validate filename to prevent directory traversal
            if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
                logger.warn("Attempted directory traversal for filename: {}", filename);
                return ResponseEntity.badRequest().build();
            }

            // Construct the full path to the file
            // This requires a new method in FileUtils to get the tempDir path
            java.nio.file.Path filePath = java.nio.file.Paths.get(fileUtils.getTempDir()).resolve(filename).normalize();
            
            Resource resource = new UrlResource(filePath.toUri());

            if (!resource.exists() || !resource.isReadable()) {
                logger.error("File not found or not readable: {}", filePath);
                return ResponseEntity.notFound().build();
            }
            
            // Determine content type based on file extension
            String contentType = "application/octet-stream";
            if (filename.endsWith(".asc")) {
                contentType = "application/pgp-keys";
            } else if (filename.endsWith(".key")) {
                contentType = "application/x-openssh-key"; // Assuming .key is for SSH private keys
            }
            
            // Set headers for secure download
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType(contentType));
            // Ensure the filename in Content-Disposition is the base name, not the full path
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"");
            headers.setCacheControl("no-cache, no-store, must-revalidate");
            headers.setPragma("no-cache");
            headers.setExpires(0);
            
            logger.info("Serving download of {} from path {}", resource.getFilename(), filePath);

            return ResponseEntity.ok()
                .headers(headers)
                .body(resource);

        } catch (java.net.MalformedURLException e) {
            logger.error("Malformed URL for file {}: {}", filename, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        } catch (Exception e) {
            logger.error("Failed to download file {}: {}", filename, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
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
        
        Map<String, Object> response = new ConcurrentHashMap<>();
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
        }
        
        response.put("valid", isValid);
        response.put("message", message);
        
        return ResponseEntity.ok(response);
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
    
    /**
     * Get client IP address from request
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
