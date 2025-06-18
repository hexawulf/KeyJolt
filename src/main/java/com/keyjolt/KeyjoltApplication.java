package com.keyjolt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot application class for KeyJolt PGP Key Generator
 * 
 * This application provides secure PGP and SSH key pair generation
 * with automatic cleanup and rate limiting features.
 */
@SpringBootApplication
@EnableAsync
@EnableScheduling
public class KeyjoltApplication {

    public static void main(String[] args) {
        // Add Bouncy Castle as security provider
        java.security.Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
        
        SpringApplication.run(KeyjoltApplication.class, args);
    }
}
