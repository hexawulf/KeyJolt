package com.keyjolt.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KeyRequestTest {

    @Test
    void validEncryptionStrengths() {
        for (int bits : new int[]{2048, 3072, 4096}) {
            KeyRequest r = new KeyRequest();
            r.setEncryptionStrength(bits);
            assertTrue(r.hasValidEncryptionStrength(), bits + " should be valid");
        }
    }

    @Test
    void invalidEncryptionStrengths() {
        for (int bits : new int[]{0, 1024, 8192, -1}) {
            KeyRequest r = new KeyRequest();
            r.setEncryptionStrength(bits);
            assertFalse(r.hasValidEncryptionStrength(), bits + " should be invalid");
        }
    }

    @Test
    void nullEncryptionStrengthIsInvalid() {
        KeyRequest r = new KeyRequest();
        assertFalse(r.hasValidEncryptionStrength());
    }

    @Test
    void nullPasswordIsValid() {
        KeyRequest r = new KeyRequest();
        r.setPassword(null);
        assertTrue(r.hasValidPassword());
    }

    @Test
    void emptyPasswordIsValid() {
        KeyRequest r = new KeyRequest();
        r.setPassword("");
        assertTrue(r.hasValidPassword());
    }

    @Test
    void shortPasswordIsInvalid() {
        KeyRequest r = new KeyRequest();
        r.setPassword("abc");
        assertFalse(r.hasValidPassword());
    }

    @Test
    void eightCharPasswordIsValid() {
        KeyRequest r = new KeyRequest();
        r.setPassword("12345678");
        assertTrue(r.hasValidPassword());
    }

    @Test
    void sanitizedNameRemovesSpecialChars() {
        KeyRequest r = new KeyRequest();
        r.setName("John Doe!");
        assertEquals("John_Doe_", r.getSanitizedName());
    }

    @Test
    void sanitizedEmailPreservesSafeChars() {
        KeyRequest r = new KeyRequest();
        r.setEmail("test@example.com");
        assertEquals("test@example.com", r.getSanitizedEmail());
    }

    @Test
    void sanitizedNameHandlesNull() {
        KeyRequest r = new KeyRequest();
        assertEquals("", r.getSanitizedName());
    }
}
