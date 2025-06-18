package com.keyjolt.model;

import jakarta.validation.constraints.*;

/**
 * Data Transfer Object for key generation requests
 */
public class KeyRequest {
    
    @NotBlank(message = "Name is required")
    @Size(max = 50, message = "Name must not exceed 50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9\\s]+$", message = "Name can only contain letters, numbers, and spaces")
    private String name;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    private String email;
    
    @NotNull(message = "Encryption strength is required")
    @Min(value = 2048, message = "Minimum encryption strength is 2048 bits")
    @Max(value = 4096, message = "Maximum encryption strength is 4096 bits")
    private Integer encryptionStrength;
    
    @NotNull(message = "Key expiry is required")
    @Min(value = 0, message = "Key expiry must be 0 or positive")
    @Max(value = 3650, message = "Maximum key expiry is 3650 days (10 years)")
    private Integer keyExpiry;
    
    private boolean generateSshKey = false;
    
    // Default constructor
    public KeyRequest() {}
    
    // Constructor with all fields
    public KeyRequest(String name, String email, Integer encryptionStrength, Integer keyExpiry, boolean generateSshKey) {
        this.name = name;
        this.email = email;
        this.encryptionStrength = encryptionStrength;
        this.keyExpiry = keyExpiry;
        this.generateSshKey = generateSshKey;
    }
    
    // Getters and setters
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public Integer getEncryptionStrength() {
        return encryptionStrength;
    }
    
    public void setEncryptionStrength(Integer encryptionStrength) {
        this.encryptionStrength = encryptionStrength;
    }
    
    public Integer getKeyExpiry() {
        return keyExpiry;
    }
    
    public void setKeyExpiry(Integer keyExpiry) {
        this.keyExpiry = keyExpiry;
    }
    
    public boolean isGenerateSshKey() {
        return generateSshKey;
    }
    
    public void setGenerateSshKey(boolean generateSshKey) {
        this.generateSshKey = generateSshKey;
    }
    
    /**
     * Get sanitized name for file naming
     */
    public String getSanitizedName() {
        return name != null ? name.replaceAll("[^a-zA-Z0-9]", "_") : "";
    }
    
    /**
     * Get sanitized email for file naming
     */
    public String getSanitizedEmail() {
        return email != null ? email.replaceAll("[^a-zA-Z0-9@.]", "_") : "";
    }
}
