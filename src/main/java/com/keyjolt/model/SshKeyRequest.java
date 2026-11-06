package com.keyjolt.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request for generating a standalone SSH key pair (no PGP).
 */
public class SshKeyRequest {

    public static final String TYPE_ED25519 = "ed25519";
    public static final String TYPE_RSA = "rsa";

    private static final int MIN_PASSPHRASE_LENGTH = 8;

    @NotBlank(message = "Key type is required")
    @Pattern(regexp = "^(ed25519|rsa)$", message = "Key type must be ed25519 or rsa")
    private String keyType = TYPE_ED25519;

    // Restricted charset so the comment can never break the .pub line.
    @Size(max = 100, message = "Comment must not exceed 100 characters")
    @Pattern(regexp = "^[A-Za-z0-9@._+\\- ]*$",
             message = "Comment can only contain letters, numbers, spaces and @ . _ + -")
    private String comment;

    @Size(max = 256, message = "Passphrase must not exceed 256 characters")
    private String passphrase;

    public SshKeyRequest() {}

    public SshKeyRequest(String keyType, String comment, String passphrase) {
        this.keyType = keyType;
        this.comment = comment;
        this.passphrase = passphrase;
    }

    public String getKeyType() { return keyType; }
    public void setKeyType(String keyType) { this.keyType = keyType; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public String getPassphrase() { return passphrase; }
    public void setPassphrase(String passphrase) { this.passphrase = passphrase; }

    public boolean isEd25519() {
        return TYPE_ED25519.equals(keyType);
    }

    public boolean hasPassphrase() {
        return passphrase != null && !passphrase.isEmpty();
    }

    /** True when no passphrase was given, or it is at least 8 characters. */
    public boolean hasValidPassphrase() {
        return !hasPassphrase() || passphrase.length() >= MIN_PASSPHRASE_LENGTH;
    }

    /** Comment with surrounding whitespace removed; empty string when absent. */
    public String getTrimmedComment() {
        return comment == null ? "" : comment.trim();
    }
}
